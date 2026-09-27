package com.chris64233.cc.wastemanifest.correction;

import com.chris64233.cc.wastemanifest.correction.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.CorrectionDecisionResponse;
import com.chris64233.cc.wastemanifest.correction.dto.CorrectionResponse;
import com.chris64233.cc.wastemanifest.correction.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.FieldChangeRequest;
import com.chris64233.cc.wastemanifest.correction.dto.FieldChangeResponse;
import com.chris64233.cc.wastemanifest.correction.dto.ManifestRecordResponse;
import com.chris64233.cc.wastemanifest.correction.dto.VersionDiffResponse;
import com.chris64233.cc.wastemanifest.correction.dto.VersionItemResponse;
import com.chris64233.cc.wastemanifest.correction.dto.VersionResponse;
import com.chris64233.cc.wastemanifest.correction.exception.CorrectionNotFoundException;
import com.chris64233.cc.wastemanifest.manifest.Manifest;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestStatus;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestEventResponse;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
import com.chris64233.cc.wastemanifest.manifest.exception.ConflictException;
import com.chris64233.cc.wastemanifest.manifest.exception.ManifestNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CorrectionService {

    private final ManifestRepository manifests;
    private final ManifestEventRepository events;
    private final ManifestVersionRepository versions;
    private final CorrectionRepository corrections;
    private final CorrectionDecisionRepository decisions;
    private final BigDecimal weightToleranceRatio;

    public CorrectionService(ManifestRepository manifests,
                             ManifestEventRepository events,
                             ManifestVersionRepository versions,
                             CorrectionRepository corrections,
                             CorrectionDecisionRepository decisions,
                             @Value("${manifest.weight-tolerance-ratio:0.05}") BigDecimal weightToleranceRatio) {
        this.manifests = manifests;
        this.events = events;
        this.versions = versions;
        this.corrections = corrections;
        this.decisions = decisions;
        this.weightToleranceRatio = weightToleranceRatio;
    }

    @Transactional
    public CorrectionResponse createCorrection(String manifestNo, CreateCorrectionRequest request) {
        Manifest manifest = lockManifest(manifestNo);

        // 幂等：同号同内容重放返回当前结果，同号异内容冲突。
        var existingCorrection = corrections.findByCorrectionNo(request.correctionNo());
        if (existingCorrection.isPresent()) {
            Correction existing = existingCorrection.get();
            if (correctionContentMatches(existing, manifestNo, request)) {
                return toCorrectionResponse(existing);
            }
            throw new ConflictException("更正号已存在且内容不一致: " + request.correctionNo());
        }

        if (manifest.getStatus() != ManifestStatus.COMPLETED) {
            throw new BusinessRuleException("仅已完成联单可发起差错更正，当前状态: " + manifest.getStatus());
        }
        if (manifest.isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，不得发起更正");
        }
        if (corrections.existsByManifestManifestNoAndStatus(manifestNo, CorrectionStatus.PENDING)) {
            throw new BusinessRuleException("同一联单同时只能有一笔活动更正");
        }
        if (request.applicantRole() == DecisionRole.REGULATOR) {
            throw new BusinessRuleException("监管方为复核方，不能作为更正申请人");
        }

        ManifestVersion base = versions.findEffectiveByManifest(manifest)
                .orElseThrow(() -> new IllegalStateException("已完成联单缺少有效版本快照: " + manifestNo));
        Map<Integer, ManifestVersionItem> baseItems = new HashMap<>();
        for (ManifestVersionItem item : base.getItems()) {
            baseItems.put(item.getItemSeq(), item);
        }

        Map<FieldKey, FieldChangeRequest> requested = new LinkedHashMap<>();
        List<CorrectionChange> changes = new ArrayList<>();
        for (FieldChangeRequest changeRequest : request.changes()) {
            FieldKey key = new FieldKey(changeRequest.field(), changeRequest.itemSeq());
            if (requested.putIfAbsent(key, changeRequest) != null) {
                throw new BusinessRuleException("同一字段在一笔更正中只能出现一次: " + key);
            }
            ManifestVersionItem item = baseItems.get(changeRequest.itemSeq());
            if (item == null) {
                throw new BusinessRuleException("明细序号不存在: " + changeRequest.itemSeq());
            }
            String oldValue = currentValue(item, changeRequest.field());
            String newValue = validateAndNormalize(changeRequest, oldValue);
            changes.add(new CorrectionChange(null, changeRequest.field(), changeRequest.itemSeq(),
                    oldValue, newValue));
        }

        DisputeImpact impact = evaluateImpact(base, changes);

        Correction correction = new Correction(request.correctionNo(), manifest, base.getVersionNo(),
                request.applicantRole(), request.reason(), request.evidenceRef(), impact);
        for (CorrectionChange change : changes) {
            correction.addChange(new CorrectionChange(correction, change.getField(),
                    change.getItemSeq(), change.getOldValue(), change.getNewValue()));
        }
        try {
            corrections.saveAndFlush(correction);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "更正号冲突或同一联单已存在活动更正: " + request.correctionNo());
        }
        return toCorrectionResponse(correction);
    }

    /**
     * 决定提交。终止性标记（冻结/版本被取代）需要随异常一起落库，
     * 因此业务规则异常不回滚；冲突异常仍整体回滚。
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public CorrectionResponse decide(String manifestNo, String correctionNo, CorrectionDecisionRequest request) {
        lockManifest(manifestNo);
        Correction correction = loadCorrection(manifestNo, correctionNo);

        // 幂等重放优先于状态检查：同号同内容直接返回当前结果。
        var existing = decisions.findByDecisionNo(request.decisionNo());
        if (existing.isPresent()) {
            CorrectionDecision decision = existing.get();
            if (decision.matches(correctionNo, request.role(), request.value(), request.occurredAt())
                    && decision.getCorrection().getManifest().getManifestNo().equals(manifestNo)) {
                return toCorrectionResponse(decision.getCorrection());
            }
            throw new ConflictException("决定事件号已存在且内容不一致: " + request.decisionNo());
        }

        if (!correction.isPending()) {
            throw new BusinessRuleException("更正已结束（" + correction.getStatus() + "），不能再提交决定");
        }

        // 确认期间出现新的监管冻结或其他版本生效：基于旧版本的更正不得落地。
        if (correction.getManifest().isRegulatoryFrozen()) {
            correction.markFrozen();
            throw new BusinessRuleException("联单已被监管冻结，更正 " + correctionNo + " 终止、不得落地");
        }
        if (correction.getBaseVersionNo() != correction.getManifest().getCurrentVersionNo()) {
            correction.markSuperseded();
            throw new BusinessRuleException(
                    "基线版本已被其他版本取代，更正 " + correctionNo + " 终止、不得落地");
        }

        Set<DecisionRole> requiredRoles = requiredRoles(correction);
        if (!requiredRoles.contains(request.role())) {
            throw new BusinessRuleException("该角色与本次更正字段无关，无需确认: " + request.role());
        }
        for (CorrectionDecision decision : correction.getDecisions()) {
            if (decision.getRole() == request.role()) {
                throw new BusinessRuleException("该角色已对本更正作出决定: " + request.role());
            }
            if (request.occurredAt().isBefore(decision.getOccurredAt())) {
                throw new BusinessRuleException(
                        "决定时间早于已有决定时间，拒绝时间倒序: " + request.occurredAt()
                                + " < " + decision.getOccurredAt());
            }
        }
        if (request.value() == DecisionValue.WITHDRAWN
                && request.role() != correction.getApplicantRole()) {
            throw new BusinessRuleException("仅更正申请方可以撤回申请");
        }

        CorrectionDecision decision = new CorrectionDecision(request.decisionNo(), correction,
                request.role(), request.value(), request.occurredAt());
        try {
            decisions.saveAndFlush(decision);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("决定事件号已存在: " + request.decisionNo());
        }
        correction.addDecision(decision);

        switch (request.value()) {
            case REJECTED -> correction.markRejected();
            case WITHDRAWN -> correction.markWithdrawn();
            case APPROVED -> {
                if (allRequiredApproved(correction, requiredRoles)) {
                    makeEffective(correction);
                }
            }
        }
        return toCorrectionResponse(correction);
    }

    @Transactional
    public ManifestDetailResponse freeze(String manifestNo) {
        Manifest manifest = lockManifest(manifestNo);
        manifest.freeze();
        // 冻结时点所有确认中的更正一并终止，不得再落地。
        for (Correction correction : corrections.findByManifestManifestNoOrderByIdAsc(manifestNo)) {
            if (correction.isPending()) {
                correction.markFrozen();
            }
        }
        return com.chris64233.cc.wastemanifest.manifest.ManifestService.toDetail(manifest);
    }

    @Transactional(readOnly = true)
    public CorrectionResponse getCorrection(String manifestNo, String correctionNo) {
        return toCorrectionResponse(loadCorrection(manifestNo, correctionNo));
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> getVersions(String manifestNo) {
        requireManifest(manifestNo);
        return versions.findByManifestNoOrderByVersionNoAsc(manifestNo).stream()
                .map(CorrectionService::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionDiffResponse getDiff(String manifestNo, int fromVersionNo, int toVersionNo) {
        requireManifest(manifestNo);
        ManifestVersion from = versions.findByManifestNoAndVersionNo(manifestNo, fromVersionNo)
                .orElseThrow(() -> new BusinessRuleException("版本不存在: " + fromVersionNo));
        ManifestVersion to = versions.findByManifestNoAndVersionNo(manifestNo, toVersionNo)
                .orElseThrow(() -> new BusinessRuleException("版本不存在: " + toVersionNo));
        return new VersionDiffResponse(fromVersionNo, toVersionNo, diffVersions(from, to));
    }

    @Transactional(readOnly = true)
    public ManifestRecordResponse getRecord(String manifestNo) {
        Manifest manifest = manifests.findByManifestNo(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
        List<ManifestVersion> versionList = versions.findByManifestNoOrderByVersionNoAsc(manifestNo);
        List<VersionDiffResponse> diffs = new ArrayList<>();
        for (int i = 1; i < versionList.size(); i++) {
            ManifestVersion from = versionList.get(i - 1);
            ManifestVersion to = versionList.get(i);
            diffs.add(new VersionDiffResponse(from.getVersionNo(), to.getVersionNo(),
                    diffVersions(from, to)));
        }
        VersionResponse current = versionList.stream()
                .filter(ManifestVersion::isEffective)
                .findFirst()
                .map(CorrectionService::toVersionResponse)
                .orElse(null);
        List<CorrectionResponse> correctionResponses = corrections
                .findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(CorrectionService::toCorrectionResponse)
                .toList();
        List<ManifestEventResponse> timeline =
                com.chris64233.cc.wastemanifest.manifest.ManifestService.toEventResponses(
                        events.findByManifestManifestNoOrderByIdAsc(manifestNo));
        return new ManifestRecordResponse(
                com.chris64233.cc.wastemanifest.manifest.ManifestService.toDetail(manifest),
                current,
                versionList.stream().map(CorrectionService::toVersionResponse).toList(),
                diffs,
                correctionResponses,
                timeline);
    }

    // ---- 生效 ----

    private void makeEffective(Correction correction) {
        Manifest manifest = correction.getManifest();
        // 锁定该联单全部版本行后分配新版本号；外层联单行锁已串行化，此处双保险。
        List<ManifestVersion> locked = versions.findByManifestForUpdate(manifest);
        ManifestVersion base = locked.stream()
                .filter(v -> v.getVersionNo() == correction.getBaseVersionNo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("基线版本缺失: " + correction.getBaseVersionNo()));
        ManifestVersion currentEffective = locked.stream()
                .filter(ManifestVersion::isEffective)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少当前有效版本"));
        if (currentEffective.getVersionNo() != base.getVersionNo()) {
            correction.markSuperseded();
            throw new BusinessRuleException("基线版本已被其他版本取代，更正不得落地");
        }
        if (manifest.isRegulatoryFrozen()) {
            correction.markFrozen();
            throw new BusinessRuleException("联单已被监管冻结，更正不得落地");
        }

        int newVersionNo = locked.stream().mapToInt(ManifestVersion::getVersionNo).max().orElse(0) + 1;
        Map<FieldKey, String> newValues = new HashMap<>();
        for (CorrectionChange change : correction.getChanges()) {
            newValues.put(new FieldKey(change.getField(), change.getItemSeq()), change.getNewValue());
        }

        // 先按基线快照套用变更，计算新明细与新申报总重，再一次固化版本。
        record ItemValues(String category, int packageCount, BigDecimal weight) {
        }
        List<ItemValues> newItems = new ArrayList<>();
        BigDecimal declaredTotal = BigDecimal.ZERO;
        for (ManifestVersionItem item : base.getItems()) {
            int seq = item.getItemSeq();
            String category = item.getWasteCategory();
            int packageCount = item.getPackageCount();
            BigDecimal weight = item.getDeclaredWeight();
            String categoryValue = newValues.get(new FieldKey(CorrectionField.WASTE_CATEGORY, seq));
            if (categoryValue != null) {
                category = categoryValue;
            }
            String packageValue = newValues.get(new FieldKey(CorrectionField.PACKAGE_COUNT, seq));
            if (packageValue != null) {
                packageCount = Integer.parseInt(packageValue);
            }
            String weightValue = newValues.get(new FieldKey(CorrectionField.WEIGHT, seq));
            if (weightValue != null) {
                weight = new BigDecimal(weightValue);
            }
            newItems.add(new ItemValues(category, packageCount, weight));
            declaredTotal = declaredTotal.add(weight);
        }

        ManifestVersion next = new ManifestVersion(manifest, newVersionNo, declaredTotal,
                base.getReceivedWeight(), base.getFinalWeight());
        int seq = 1;
        for (ItemValues values : newItems) {
            next.addItem(new ManifestVersionItem(next, seq++, values.category(),
                    values.packageCount(), values.weight()));
        }
        versions.saveAndFlush(next);
        currentEffective.supersede();
        manifest.applyNewVersion(newVersionNo);
        correction.markEffective(newVersionNo);
    }

    // ---- 影响评估 ----

    private DisputeImpact evaluateImpact(ManifestVersion base, List<CorrectionChange> changes) {
        boolean categoryChanged = changes.stream()
                .anyMatch(c -> c.getField() == CorrectionField.WASTE_CATEGORY);
        BigDecimal weightDelta = BigDecimal.ZERO;
        for (CorrectionChange change : changes) {
            if (change.getField() == CorrectionField.WEIGHT) {
                weightDelta = weightDelta.add(change.newDecimal().subtract(change.oldDecimal()));
            }
        }
        if (weightDelta.signum() == 0) {
            return categoryChanged ? DisputeImpact.WASTE_CATEGORY_CHANGED : DisputeImpact.NONE;
        }
        BigDecimal declared = base.getDeclaredTotalWeight();
        BigDecimal received = base.getReceivedWeight();
        boolean originallyDisputed = disputed(received, declared);
        boolean correctedDisputed = disputed(received, declared.add(weightDelta));
        if (!originallyDisputed && correctedDisputed) {
            return DisputeImpact.WOULD_HAVE_ENTERED_DISPUTE;
        }
        if (originallyDisputed && !correctedDisputed) {
            return DisputeImpact.DISPUTE_WOULD_HAVE_BEEN_AVOIDED;
        }
        return categoryChanged ? DisputeImpact.WASTE_CATEGORY_CHANGED : DisputeImpact.NONE;
    }

    private boolean disputed(BigDecimal received, BigDecimal declared) {
        if (received == null) {
            return false;
        }
        BigDecimal diff = received.subtract(declared).abs();
        BigDecimal allowed = declared.multiply(weightToleranceRatio);
        return diff.compareTo(allowed) > 0;
    }

    // ---- 校验辅助 ----

    /** 幂等匹配：联单、申请人、理由、证据与全部变更逐项一致。 */
    private boolean correctionContentMatches(Correction correction, String manifestNo,
                                             CreateCorrectionRequest request) {
        if (!correction.getManifest().getManifestNo().equals(manifestNo)
                || correction.getApplicantRole() != request.applicantRole()
                || !correction.getReason().equals(request.reason())
                || !correction.getEvidenceRef().equals(request.evidenceRef())
                || correction.getChanges().size() != request.changes().size()) {
            return false;
        }
        Set<String> expected = new HashSet<>();
        for (CorrectionChange change : correction.getChanges()) {
            expected.add(change.getField() + "|" + change.getItemSeq() + "|" + change.getNewValue());
        }
        for (FieldChangeRequest change : request.changes()) {
            String normalized;
            try {
                normalized = switch (change.field()) {
                    case PACKAGE_COUNT -> String.valueOf(Integer.parseInt(change.newValue().trim()));
                    case WEIGHT -> new BigDecimal(change.newValue().trim())
                            .setScale(3, RoundingMode.UNNECESSARY).toPlainString();
                    case WASTE_CATEGORY -> change.newValue().trim();
                };
            } catch (RuntimeException e) {
                return false;
            }
            if (!expected.remove(change.field() + "|" + change.itemSeq() + "|" + normalized)) {
                return false;
            }
        }
        return expected.isEmpty();
    }

    private Set<DecisionRole> requiredRoles(Correction correction) {
        EnumSet<DecisionRole> roles = EnumSet.noneOf(DecisionRole.class);
        for (CorrectionChange change : correction.getChanges()) {
            switch (change.getField()) {
                case PACKAGE_COUNT -> {
                    roles.add(DecisionRole.GENERATOR);
                    roles.add(DecisionRole.TRANSPORTER);
                    roles.add(DecisionRole.DISPOSER);
                }
                case WEIGHT, WASTE_CATEGORY -> {
                    roles.add(DecisionRole.GENERATOR);
                    roles.add(DecisionRole.DISPOSER);
                }
            }
        }
        boolean categoryChanged = correction.getChanges().stream()
                .anyMatch(c -> c.getField() == CorrectionField.WASTE_CATEGORY);
        if (categoryChanged) {
            roles.add(DecisionRole.REGULATOR);
        }
        return roles;
    }

    private boolean allRequiredApproved(Correction correction, Set<DecisionRole> requiredRoles) {
        Set<DecisionRole> approved = EnumSet.noneOf(DecisionRole.class);
        for (CorrectionDecision decision : correction.getDecisions()) {
            if (decision.getValue() == DecisionValue.APPROVED) {
                approved.add(decision.getRole());
            }
        }
        return approved.containsAll(requiredRoles);
    }

    private String currentValue(ManifestVersionItem item, CorrectionField field) {
        return switch (field) {
            case PACKAGE_COUNT -> String.valueOf(item.getPackageCount());
            case WEIGHT -> item.getDeclaredWeight().toPlainString();
            case WASTE_CATEGORY -> item.getWasteCategory();
        };
    }

    private String validateAndNormalize(FieldChangeRequest request, String oldValue) {
        String newValue = request.newValue().trim();
        switch (request.field()) {
            case PACKAGE_COUNT -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(newValue);
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException("包装数量必须为整数: " + newValue);
                }
                if (parsed <= 0) {
                    throw new BusinessRuleException("包装数量必须为正: " + newValue);
                }
                if (parsed == Integer.parseInt(oldValue)) {
                    throw new BusinessRuleException("新值与原始值相同，无需更正");
                }
                return String.valueOf(parsed);
            }
            case WEIGHT -> {
                BigDecimal parsed;
                try {
                    parsed = new BigDecimal(newValue);
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException("重量格式非法: " + newValue);
                }
                if (parsed.signum() <= 0) {
                    throw new BusinessRuleException("重量必须为正: " + newValue);
                }
                try {
                    parsed = parsed.setScale(3, RoundingMode.UNNECESSARY);
                } catch (ArithmeticException e) {
                    throw new BusinessRuleException("重量最多保留 3 位小数: " + newValue);
                }
                if (parsed.compareTo(new BigDecimal(oldValue)) == 0) {
                    throw new BusinessRuleException("新值与原始值相同，无需更正");
                }
                return parsed.toPlainString();
            }
            case WASTE_CATEGORY -> {
                if (newValue.isEmpty() || newValue.length() > 64) {
                    throw new BusinessRuleException("废物类别长度需在 1~64 之间");
                }
                if (newValue.equals(oldValue)) {
                    throw new BusinessRuleException("新值与原始值相同，无需更正");
                }
                return newValue;
            }
        }
        throw new IllegalStateException("未知更正字段: " + request.field());
    }

    // ---- 加载辅助 ----

    private Manifest lockManifest(String manifestNo) {
        return manifests.findByManifestNoForUpdate(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
    }

    private void requireManifest(String manifestNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
    }

    private Correction loadCorrection(String manifestNo, String correctionNo) {
        requireManifest(manifestNo);
        Correction correction = corrections.findByCorrectionNo(correctionNo)
                .orElseThrow(() -> new CorrectionNotFoundException(correctionNo));
        if (!correction.getManifest().getManifestNo().equals(manifestNo)) {
            throw new CorrectionNotFoundException(correctionNo);
        }
        return correction;
    }

    // ---- 映射 ----

    private static List<FieldChangeResponse> diffVersions(ManifestVersion from, ManifestVersion to) {
        Map<Integer, ManifestVersionItem> fromItems = new HashMap<>();
        for (ManifestVersionItem item : from.getItems()) {
            fromItems.put(item.getItemSeq(), item);
        }
        List<FieldChangeResponse> changes = new ArrayList<>();
        for (ManifestVersionItem toItem : to.getItems()) {
            ManifestVersionItem fromItem = fromItems.get(toItem.getItemSeq());
            if (fromItem == null) {
                continue;
            }
            if (!fromItem.getWasteCategory().equals(toItem.getWasteCategory())) {
                changes.add(new FieldChangeResponse(CorrectionField.WASTE_CATEGORY, toItem.getItemSeq(),
                        fromItem.getWasteCategory(), toItem.getWasteCategory()));
            }
            if (fromItem.getPackageCount() != toItem.getPackageCount()) {
                changes.add(new FieldChangeResponse(CorrectionField.PACKAGE_COUNT, toItem.getItemSeq(),
                        String.valueOf(fromItem.getPackageCount()), String.valueOf(toItem.getPackageCount())));
            }
            if (fromItem.getDeclaredWeight().compareTo(toItem.getDeclaredWeight()) != 0) {
                changes.add(new FieldChangeResponse(CorrectionField.WEIGHT, toItem.getItemSeq(),
                        fromItem.getDeclaredWeight().toPlainString(),
                        toItem.getDeclaredWeight().toPlainString()));
            }
        }
        return changes;
    }

    private static CorrectionResponse toCorrectionResponse(Correction correction) {
        List<FieldChangeResponse> changeResponses = correction.getChanges().stream()
                .map(c -> new FieldChangeResponse(c.getField(), c.getItemSeq(),
                        c.getOldValue(), c.getNewValue()))
                .toList();
        List<CorrectionDecisionResponse> decisionResponses = correction.getDecisions().stream()
                .map(d -> new CorrectionDecisionResponse(d.getDecisionNo(), d.getRole(),
                        d.getValue(), d.getOccurredAt(), d.getRecordedAt()))
                .toList();
        return new CorrectionResponse(correction.getCorrectionNo(),
                correction.getManifest().getManifestNo(), correction.getBaseVersionNo(),
                correction.getResultVersionNo(), correction.getStatus(), correction.getApplicantRole(),
                correction.getReason(), correction.getEvidenceRef(), correction.getDisputeImpact(),
                changeResponses, decisionResponses, correction.getCreatedAt(), correction.getClosedAt());
    }

    private static VersionResponse toVersionResponse(ManifestVersion version) {
        List<VersionItemResponse> items = version.getItems().stream()
                .map(i -> new VersionItemResponse(i.getItemSeq(), i.getWasteCategory(),
                        i.getPackageCount(), i.getDeclaredWeight()))
                .toList();
        return new VersionResponse(version.getVersionNo(), version.getStatus(),
                version.getDeclaredTotalWeight(), version.getReceivedWeight(),
                version.getFinalWeight(), items, version.getEffectiveAt());
    }

    private record FieldKey(CorrectionField field, int itemSeq) {
    }
}
