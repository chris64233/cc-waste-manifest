package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionChangeRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionChangeView;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionDecisionView;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestRevisionsResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestVersionView;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
import com.chris64233.cc.wastemanifest.manifest.exception.ConflictException;
import com.chris64233.cc.wastemanifest.manifest.exception.CorrectionNotFoundException;
import com.chris64233.cc.wastemanifest.manifest.exception.ManifestNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CorrectionService {

    private final ManifestRepository manifests;
    private final ManifestVersionRepository versions;
    private final ManifestCorrectionRepository corrections;
    private final CorrectionDecisionRepository decisions;
    private final BigDecimal weightToleranceRatio;

    public CorrectionService(ManifestRepository manifests,
                             ManifestVersionRepository versions,
                             ManifestCorrectionRepository corrections,
                             CorrectionDecisionRepository decisions,
                             @Value("${manifest.weight-tolerance-ratio:0.05}") BigDecimal weightToleranceRatio) {
        this.manifests = manifests;
        this.versions = versions;
        this.corrections = corrections;
        this.decisions = decisions;
        this.weightToleranceRatio = weightToleranceRatio;
    }

    @Transactional
    public CorrectionResponse createCorrection(String manifestNo, CreateCorrectionRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        List<ChangeSpec> requested = parseNewValues(request.changes());

        var existing = corrections.findByCorrectionNo(request.correctionNo());
        if (existing.isPresent()) {
            ManifestCorrection correction = existing.get();
            if (correction.getManifest().getManifestNo().equals(manifestNo)
                    && correction.getReason().equals(request.reason())
                    && correction.getEvidence().equals(request.evidence())
                    && changesMatch(correction.getChanges(), requested)) {
                return toResponse(correction);
            }
            throw new ConflictException("更正号已存在且内容不一致: " + request.correctionNo());
        }

        if (manifest.getStatus() != ManifestStatus.COMPLETED) {
            throw new BusinessRuleException("仅已完成联单可发起差错更正，当前状态: " + manifest.getStatus());
        }
        if (manifest.isFrozen()) {
            throw new BusinessRuleException("联单处于监管冻结状态，禁止发起更正: " + manifestNo);
        }
        if (corrections.existsByManifestAndStatus(manifest, CorrectionStatus.PENDING)) {
            throw new ConflictException("同一联单同时只能有一笔活动更正: " + manifestNo);
        }

        ManifestVersion baseVersion = currentVersion(manifest);
        List<ChangeSpec> changes = fillOriginalValues(requested, baseVersion.getItems());
        List<ApproverRole> requiredRoles = requiredRolesOf(changes);
        DisputeImpact impact = evaluateImpact(manifest, correctedTotal(baseVersion.getItems(), changes));

        ManifestCorrection correction = new ManifestCorrection(manifest, request.correctionNo(),
                manifest.getCurrentVersionNo(), request.reason(), request.evidence(), impact, requiredRoles);
        for (ChangeSpec change : changes) {
            correction.addChange(new CorrectionChange(correction, change.itemIndex(), change.field(),
                    change.oldValue(), change.newValue()));
        }
        try {
            corrections.saveAndFlush(correction);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("更正号已存在: " + request.correctionNo());
        }
        return toResponse(correction);
    }

    @Transactional
    public CorrectionResponse decide(String manifestNo, String correctionNo, CorrectionDecisionRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        ManifestCorrection correction = corrections.findByCorrectionNo(correctionNo)
                .filter(c -> c.getManifest().getManifestNo().equals(manifestNo))
                .orElseThrow(() -> new CorrectionNotFoundException(correctionNo));

        var existing = decisions.findByDecisionNo(request.decisionNo());
        if (existing.isPresent()) {
            CorrectionDecision decision = existing.get();
            if (decision.matches(correctionNo, request.role(), request.decision(), request.occurredAt())) {
                return toResponse(correction);
            }
            throw new ConflictException("决定事件号已存在且内容不一致: " + request.decisionNo());
        }

        if (correction.getStatus() != CorrectionStatus.PENDING) {
            throw new BusinessRuleException("更正已终结，禁止继续确认: " + correctionNo + " 状态 " + correction.getStatus());
        }
        if (manifest.isFrozen()) {
            correction.invalidate();
            throw new BusinessRuleException("确认期间出现监管冻结，基于旧版本的更正不得落地: " + correctionNo);
        }
        if (correction.getBaseVersionNo() != manifest.getCurrentVersionNo()) {
            correction.invalidate();
            throw new BusinessRuleException("已有其他版本生效，基于旧版本的更正不得落地: " + correctionNo);
        }
        if (!correction.requiresRole(request.role())) {
            throw new BusinessRuleException("该角色无需参与本更正的确认: " + request.role());
        }

        try {
            decisions.saveAndFlush(new CorrectionDecision(correction, request.decisionNo(),
                    request.role(), request.decision(), request.occurredAt()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("决定事件号已存在: " + request.decisionNo());
        }

        Map<ApproverRole, DecisionType> latestByRole = latestDecisions(correction);
        if (latestByRole.containsValue(DecisionType.REJECT)) {
            correction.reject();
        } else if (latestByRole.containsValue(DecisionType.WITHDRAW)) {
            correction.invalidate();
        } else if (correction.getRequiredRoles().stream()
                .allMatch(role -> latestByRole.get(role) == DecisionType.APPROVE)) {
            applyCorrection(manifest, correction);
        }
        return toResponse(correction);
    }

    @Transactional(readOnly = true)
    public CorrectionResponse getCorrection(String manifestNo, String correctionNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
        return corrections.findByCorrectionNo(correctionNo)
                .filter(c -> c.getManifest().getManifestNo().equals(manifestNo))
                .map(this::toResponse)
                .orElseThrow(() -> new CorrectionNotFoundException(correctionNo));
    }

    @Transactional(readOnly = true)
    public ManifestRevisionsResponse getRevisions(String manifestNo) {
        Manifest manifest = manifests.findByManifestNo(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
        List<ManifestVersionView> versionViews = new ArrayList<>();
        ManifestVersion previous = null;
        for (ManifestVersion version : versions.findByManifestManifestNoOrderByVersionNoAsc(manifestNo)) {
            List<CorrectionChangeView> diff = previous == null ? List.of() : diffVersions(previous, version);
            versionViews.add(new ManifestVersionView(version.getVersionNo(), version.getSource(),
                    version.getCorrectionNo(), version.getDeclaredTotalWeight(), toItemResponses(version),
                    diff, version.getCreatedAt()));
            previous = version;
        }
        List<CorrectionResponse> correctionResponses = corrections
                .findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(this::toResponse)
                .toList();
        return new ManifestRevisionsResponse(manifestNo, manifest.getCurrentVersionNo(),
                versionViews, correctionResponses);
    }

    private void applyCorrection(Manifest manifest, ManifestCorrection correction) {
        if (manifest.isFrozen() || correction.getBaseVersionNo() != manifest.getCurrentVersionNo()) {
            correction.invalidate();
            throw new BusinessRuleException("确认期间联单状态已变化，基于旧版本的更正不得落地: "
                    + correction.getCorrectionNo());
        }
        ManifestVersion baseVersion = versions
                .findByManifestAndVersionNo(manifest, correction.getBaseVersionNo())
                .orElseThrow(() -> new IllegalStateException("更正基准版本缺失: " + correction.getCorrectionNo()));
        Map<String, String> changeByItemAndField = new HashMap<>();
        for (CorrectionChange change : correction.getChanges()) {
            changeByItemAndField.put(change.getItemIndex() + ":" + change.getField(), change.getNewValue());
        }
        int newVersionNo = manifest.getCurrentVersionNo() + 1;
        ManifestVersion newVersion = new ManifestVersion(manifest, newVersionNo,
                VersionSource.CORRECTION, correction.getCorrectionNo());
        BigDecimal total = BigDecimal.ZERO;
        for (ManifestVersionItem baseItem : baseVersion.getItems()) {
            String category = changeByItemAndField.getOrDefault(
                    baseItem.getItemIndex() + ":" + CorrectionField.WASTE_CATEGORY, baseItem.getWasteCategory());
            int packageCount = Integer.parseInt(changeByItemAndField.getOrDefault(
                    baseItem.getItemIndex() + ":" + CorrectionField.PACKAGE_COUNT,
                    String.valueOf(baseItem.getPackageCount())));
            BigDecimal weight = new BigDecimal(changeByItemAndField.getOrDefault(
                    baseItem.getItemIndex() + ":" + CorrectionField.WEIGHT,
                    normalize(baseItem.getDeclaredWeight()).toPlainString()));
            newVersion.addItem(new ManifestVersionItem(newVersion, baseItem.getItemIndex(),
                    category, packageCount, weight));
            total = total.add(weight);
        }
        newVersion.setDeclaredTotalWeight(total);
        versions.save(newVersion);
        manifest.applyCorrectedVersion(newVersionNo, total);
        correction.apply(newVersionNo);
    }

    private DisputeImpact evaluateImpact(Manifest manifest, BigDecimal correctedTotal) {
        BigDecimal received = manifest.getReceivedWeight();
        BigDecimal originalDeclared = versions.findByManifestAndVersionNo(manifest, 1)
                .orElseThrow(() -> new IllegalStateException("联单原始版本缺失: " + manifest.getManifestNo()))
                .getDeclaredTotalWeight();
        boolean wasDispute = exceedsTolerance(originalDeclared, received);
        boolean wouldDispute = exceedsTolerance(correctedTotal, received);
        if (!wasDispute && !wouldDispute) {
            return DisputeImpact.CONCLUSION_UNCHANGED;
        }
        if (wasDispute && !wouldDispute) {
            return DisputeImpact.DISPUTE_WOULD_NOT_HAVE_OCCURRED;
        }
        if (!wasDispute) {
            return DisputeImpact.DISPUTE_WOULD_HAVE_OCCURRED;
        }
        return DisputeImpact.DISPUTE_WOULD_REMAIN;
    }

    private boolean exceedsTolerance(BigDecimal declared, BigDecimal actual) {
        BigDecimal diff = actual.subtract(declared).abs();
        return diff.compareTo(declared.multiply(weightToleranceRatio)) > 0;
    }

    private BigDecimal correctedTotal(List<ManifestVersionItem> baseItems, List<ChangeSpec> changes) {
        Map<String, String> weightByItem = new HashMap<>();
        for (ChangeSpec change : changes) {
            if (change.field() == CorrectionField.WEIGHT) {
                weightByItem.put(String.valueOf(change.itemIndex()), change.newValue());
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ManifestVersionItem item : baseItems) {
            String weight = weightByItem.getOrDefault(String.valueOf(item.getItemIndex()),
                    normalize(item.getDeclaredWeight()).toPlainString());
            total = total.add(new BigDecimal(weight));
        }
        return total;
    }

    private List<ChangeSpec> parseNewValues(List<CorrectionChangeRequest> requests) {
        Set<String> seen = new HashSet<>();
        List<ChangeSpec> parsed = new ArrayList<>();
        for (CorrectionChangeRequest request : requests) {
            String key = request.itemIndex() + ":" + request.field();
            if (!seen.add(key)) {
                throw new BusinessRuleException("同一明细的同一字段重复更正: 明细 " + request.itemIndex()
                        + " 字段 " + request.field());
            }
            String newValue = switch (request.field()) {
                case PACKAGE_COUNT -> String.valueOf(parsePositiveInt(request.newValue()));
                case WEIGHT -> parseWeight(request.newValue()).toPlainString();
                case WASTE_CATEGORY -> parseCategory(request.newValue());
            };
            parsed.add(new ChangeSpec(request.itemIndex(), request.field(), null, newValue));
        }
        return parsed;
    }

    private List<ChangeSpec> fillOriginalValues(List<ChangeSpec> requested, List<ManifestVersionItem> baseItems) {
        List<ChangeSpec> filled = new ArrayList<>();
        for (ChangeSpec change : requested) {
            if (change.itemIndex() >= baseItems.size()) {
                throw new BusinessRuleException("明细序号超出范围: " + change.itemIndex()
                        + "，当前版本共 " + baseItems.size() + " 条明细");
            }
            ManifestVersionItem item = baseItems.get(change.itemIndex());
            String oldValue = switch (change.field()) {
                case PACKAGE_COUNT -> String.valueOf(item.getPackageCount());
                case WEIGHT -> normalize(item.getDeclaredWeight()).toPlainString();
                case WASTE_CATEGORY -> item.getWasteCategory();
            };
            if (oldValue.equals(change.newValue())) {
                throw new BusinessRuleException("更正新值与原始值相同: 明细 " + change.itemIndex()
                        + " 字段 " + change.field());
            }
            filled.add(new ChangeSpec(change.itemIndex(), change.field(), oldValue, change.newValue()));
        }
        return filled;
    }

    private static List<ApproverRole> requiredRolesOf(List<ChangeSpec> changes) {
        Set<ApproverRole> roles = EnumSet.noneOf(ApproverRole.class);
        for (ChangeSpec change : changes) {
            switch (change.field()) {
                case PACKAGE_COUNT -> {
                    roles.add(ApproverRole.GENERATOR);
                    roles.add(ApproverRole.TRANSPORTER);
                }
                case WEIGHT -> {
                    roles.add(ApproverRole.GENERATOR);
                    roles.add(ApproverRole.DISPOSER);
                }
                case WASTE_CATEGORY -> {
                    roles.add(ApproverRole.GENERATOR);
                    roles.add(ApproverRole.DISPOSER);
                    roles.add(ApproverRole.REGULATOR);
                }
            }
        }
        return List.copyOf(roles);
    }

    private Map<ApproverRole, DecisionType> latestDecisions(ManifestCorrection correction) {
        Map<ApproverRole, DecisionType> latest = new EnumMap<>(ApproverRole.class);
        for (CorrectionDecision decision : decisions.findByCorrectionOrderByIdAsc(correction)) {
            latest.put(decision.getRole(), decision.getDecision());
        }
        return latest;
    }

    private boolean changesMatch(List<CorrectionChange> stored, List<ChangeSpec> requested) {
        List<String> storedKeys = stored.stream()
                .map(change -> change.getItemIndex() + ":" + change.getField() + ":" + change.getNewValue())
                .sorted()
                .toList();
        List<String> requestedKeys = requested.stream()
                .map(change -> change.itemIndex() + ":" + change.field() + ":" + change.newValue())
                .sorted()
                .toList();
        return storedKeys.equals(requestedKeys);
    }

    private static List<CorrectionChangeView> diffVersions(ManifestVersion previous, ManifestVersion current) {
        List<CorrectionChangeView> diff = new ArrayList<>();
        List<ManifestVersionItem> previousItems = previous.getItems();
        List<ManifestVersionItem> currentItems = current.getItems();
        int common = Math.min(previousItems.size(), currentItems.size());
        for (int i = 0; i < common; i++) {
            ManifestVersionItem before = previousItems.get(i);
            ManifestVersionItem after = currentItems.get(i);
            if (!before.getWasteCategory().equals(after.getWasteCategory())) {
                diff.add(new CorrectionChangeView(i, CorrectionField.WASTE_CATEGORY,
                        before.getWasteCategory(), after.getWasteCategory()));
            }
            if (before.getPackageCount() != after.getPackageCount()) {
                diff.add(new CorrectionChangeView(i, CorrectionField.PACKAGE_COUNT,
                        String.valueOf(before.getPackageCount()), String.valueOf(after.getPackageCount())));
            }
            if (before.getDeclaredWeight().compareTo(after.getDeclaredWeight()) != 0) {
                diff.add(new CorrectionChangeView(i, CorrectionField.WEIGHT,
                        normalize(before.getDeclaredWeight()).toPlainString(),
                        normalize(after.getDeclaredWeight()).toPlainString()));
            }
        }
        diff.sort(Comparator.comparing(CorrectionChangeView::itemIndex)
                .thenComparing(view -> view.field().name()));
        return diff;
    }

    private ManifestVersion currentVersion(Manifest manifest) {
        return versions.findByManifestAndVersionNo(manifest, manifest.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException(
                        "联单当前有效版本缺失: " + manifest.getManifestNo() + " v" + manifest.getCurrentVersionNo()));
    }

    private Manifest lockManifest(String manifestNo) {
        return manifests.findByManifestNoForUpdate(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new BusinessRuleException("包装数量必须为正整数: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("包装数量格式非法: " + value);
        }
    }

    private static BigDecimal parseWeight(String value) {
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("重量格式非法: " + value);
        }
        if (parsed.signum() <= 0) {
            throw new BusinessRuleException("重量必须为正: " + value);
        }
        try {
            return parsed.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("重量最多保留 3 位小数: " + value);
        }
    }

    private static String parseCategory(String value) {
        String category = value.trim();
        if (category.isEmpty() || category.length() > 64) {
            throw new BusinessRuleException("废物类别非法: " + value);
        }
        return category;
    }

    private static BigDecimal normalize(BigDecimal weight) {
        return weight.setScale(3, RoundingMode.UNNECESSARY);
    }

    private static List<ManifestItemResponse> toItemResponses(ManifestVersion version) {
        return version.getItems().stream()
                .map(item -> new ManifestItemResponse(item.getWasteCategory(),
                        item.getPackageCount(), item.getDeclaredWeight()))
                .toList();
    }

    private CorrectionResponse toResponse(ManifestCorrection correction) {
        List<CorrectionChangeView> changeViews = correction.getChanges().stream()
                .map(change -> new CorrectionChangeView(change.getItemIndex(), change.getField(),
                        change.getOldValue(), change.getNewValue()))
                .toList();
        List<CorrectionDecisionView> decisionViews = decisions.findByCorrectionOrderByIdAsc(correction).stream()
                .map(decision -> new CorrectionDecisionView(decision.getDecisionNo(), decision.getRole(),
                        decision.getDecision(), decision.getOccurredAt(), decision.getRecordedAt()))
                .toList();
        return new CorrectionResponse(correction.getCorrectionNo(),
                correction.getManifest().getManifestNo(), correction.getStatus(),
                correction.getBaseVersionNo(), correction.getResultVersionNo(),
                correction.getReason(), correction.getEvidence(), correction.getDisputeImpact(),
                correction.getRequiredRoles(), changeViews, decisionViews,
                correction.getCreatedAt(), correction.getClosedAt());
    }

    private record ChangeSpec(int itemIndex, CorrectionField field, String oldValue, String newValue) {
    }
}
