package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.correction.ManifestVersion;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionItem;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.incident.PackageFreezeRepository;
import com.chris64233.cc.wastemanifest.incident.PackageFreezeStatus;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.DisputeConfirmRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestEventResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestItemResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManifestService {

    private final ManifestRepository manifests;
    private final ManifestEventRepository events;
    private final ManifestVersionRepository versions;
    private final ManifestFlowAdjustmentRepository flowAdjustments;
    private final PackageFreezeRepository packageFreezes;
    private final ActiveOpGuard activeOps;
    private final BigDecimal weightToleranceRatio;

    public ManifestService(ManifestRepository manifests,
                           ManifestEventRepository events,
                           ManifestVersionRepository versions,
                           ManifestFlowAdjustmentRepository flowAdjustments,
                           PackageFreezeRepository packageFreezes,
                           ActiveOpGuard activeOps,
                           @Value("${manifest.weight-tolerance-ratio:0.05}") BigDecimal weightToleranceRatio) {
        this.manifests = manifests;
        this.events = events;
        this.versions = versions;
        this.flowAdjustments = flowAdjustments;
        this.packageFreezes = packageFreezes;
        this.activeOps = activeOps;
        this.weightToleranceRatio = weightToleranceRatio;
    }

    @Transactional
    public ManifestDetailResponse create(CreateManifestRequest request) {
        if (manifests.existsByManifestNo(request.manifestNo())) {
            throw new ConflictException("联单号已存在: " + request.manifestNo());
        }
        Manifest manifest = new Manifest(request.manifestNo(), request.generatorId(),
                request.transporterId(), request.disposerId());
        BigDecimal total = BigDecimal.ZERO;
        for (var itemRequest : request.items()) {
            BigDecimal weight = normalize(itemRequest.declaredWeight());
            manifest.addItem(new ManifestItem(manifest, itemRequest.wasteCategory(),
                    itemRequest.packageCount(), weight));
            total = total.add(weight);
        }
        manifest.setDeclaredTotalWeight(total);
        manifests.save(manifest);
        return toDetail(manifest);
    }

    @Transactional
    public ManifestDetailResponse handover(String manifestNo, HandoverRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        EventType type = handoverTypeOf(request.role());
        BigDecimal weight = normalize(request.weight());

        var existing = events.findByEventNo(request.eventNo());
        if (existing.isPresent()) {
            ManifestEvent event = existing.get();
            if (event.matches(manifestNo, type, request.role(), weight, request.occurredAt())) {
                return toDetail(manifest);
            }
            throw new ConflictException("事件号已存在且内容不一致: " + request.eventNo());
        }

        CustodianRole expectedRole = switch (manifest.getStatus()) {
            case CREATED -> CustodianRole.GENERATOR;
            case IN_TRANSIT -> CustodianRole.TRANSPORTER;
            case RECEIVED_BY_TRANSPORTER -> CustodianRole.DISPOSER;
            case WEIGHT_DISPUTE, COMPLETED, CLOSED ->
                    throw new BusinessRuleException("当前状态不允许交接: " + manifest.getStatus());
        };
        if (request.role() != expectedRole) {
            throw new BusinessRuleException("交接顺序错误，当前应由 " + expectedRole + " 提交，实际为 " + request.role());
        }
        if (manifest.isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，禁止交接");
        }
        // 存在确认中的异常处置（泄漏/遗失冻结或改道待决）时不能继续正常交接。
        ActiveManifestOp activeOp = activeOps.find(manifestNo);
        if (activeOp != null && activeOp.getOpType() == ActiveOpType.INCIDENT
                || packageFreezes.existsByManifestManifestNoAndStatus(
                        manifestNo, PackageFreezeStatus.FROZEN)) {
            throw new BusinessRuleException("存在确认中的异常处置或冻结包装，不能继续正常交接");
        }

        List<ManifestEvent> timeline = events.findByManifestOrderByIdAsc(manifest);
        rejectTimeReversal(timeline, request.occurredAt());

        saveEvent(new ManifestEvent(manifest, request.eventNo(), type, request.role(), weight, request.occurredAt()));
        switch (type) {
            case GENERATOR_SHIP -> manifest.markInTransit();
            case TRANSPORTER_RECEIVE -> manifest.markReceivedByTransporter();
            case DISPOSER_RECEIVE -> {
                settleByWeight(manifest, weight);
                if (manifest.getStatus() == ManifestStatus.COMPLETED) {
                    snapshotInitialVersion(manifest);
                }
            }            default -> throw new IllegalStateException("非交接事件类型: " + type);
        }
        return toDetail(manifest);
    }

    @Transactional
    public ManifestDetailResponse confirmDispute(String manifestNo, DisputeConfirmRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        if (request.role() != CustodianRole.GENERATOR && request.role() != CustodianRole.DISPOSER) {
            throw new BusinessRuleException("重量争议仅允许产生方或处置方确认，实际为 " + request.role());
        }
        BigDecimal confirmedWeight = normalize(request.confirmedWeight());

        var existing = events.findByEventNo(request.eventNo());
        if (existing.isPresent()) {
            ManifestEvent event = existing.get();
            if (event.matches(manifestNo, EventType.DISPUTE_CONFIRM, request.role(),
                    confirmedWeight, request.occurredAt())) {
                return toDetail(manifest);
            }
            throw new ConflictException("事件号已存在且内容不一致: " + request.eventNo());
        }

        if (manifest.getStatus() != ManifestStatus.WEIGHT_DISPUTE) {
            throw new BusinessRuleException("当前状态不存在重量争议: " + manifest.getStatus());
        }

        List<ManifestEvent> timeline = events.findByManifestOrderByIdAsc(manifest);
        rejectTimeReversal(timeline, request.occurredAt());

        saveEvent(new ManifestEvent(manifest, request.eventNo(), EventType.DISPUTE_CONFIRM,
                request.role(), confirmedWeight, request.occurredAt()));

        BigDecimal generatorWeight = latestConfirmWeight(timeline, request, CustodianRole.GENERATOR, confirmedWeight);
        BigDecimal disposerWeight = latestConfirmWeight(timeline, request, CustodianRole.DISPOSER, confirmedWeight);
        if (generatorWeight != null && disposerWeight != null
                && generatorWeight.compareTo(disposerWeight) == 0) {
            manifest.markCompleted(manifest.getReceivedWeight(), generatorWeight);
            snapshotInitialVersion(manifest);
        }
        return toDetail(manifest);
    }

    @Transactional(readOnly = true)
    public ManifestDetailResponse getDetail(String manifestNo) {
        return toDetail(manifests.findByManifestNo(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo)));
    }

    @Transactional(readOnly = true)
    public List<ManifestEventResponse> getTimeline(String manifestNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
        return events.findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(ManifestService::toEventResponse)
                .toList();
    }

    private Manifest lockManifest(String manifestNo) {
        return manifests.findByManifestNoForUpdate(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
    }

    private void settleByWeight(Manifest manifest, BigDecimal receivedWeight) {
        BigDecimal declared = remainingDeclaredTotal(manifest);
        BigDecimal diff = receivedWeight.subtract(declared).abs();
        BigDecimal allowed = declared.multiply(weightToleranceRatio);
        if (diff.compareTo(allowed) <= 0) {
            manifest.markCompleted(receivedWeight, receivedWeight);
        } else {
            manifest.markWeightDispute(receivedWeight);
        }
    }

    /** 继续沿原联单流转的申报总重 = 原始申报总重 - 异常方案拆出重量。 */
    private BigDecimal remainingDeclaredTotal(Manifest manifest) {
        BigDecimal detached = flowAdjustments.findByManifestOrderByItemSeqAsc(manifest).stream()
                .map(ManifestFlowAdjustment::getDetachedWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return manifest.getDeclaredTotalWeight().subtract(detached);
    }

    /** 各明细继续流转的包装数 = 原始包装数 - 异常方案拆出包装数。 */
    private Map<Integer, Integer> remainingPackageCounts(Manifest manifest) {
        Map<Integer, Integer> detached = new HashMap<>();
        for (ManifestFlowAdjustment adjustment : flowAdjustments.findByManifestOrderByItemSeqAsc(manifest)) {
            detached.merge(adjustment.getItemSeq(), adjustment.getDetachedPackageCount(), Integer::sum);
        }
        Map<Integer, Integer> remaining = new HashMap<>();
        int seq = 1;
        for (ManifestItem item : manifest.getItems()) {
            remaining.put(seq, item.getPackageCount() - detached.getOrDefault(seq, 0));
            seq++;
        }
        return remaining;
    }

    /** 联单完成时固化版本 1 快照；若异常方案已拆出部分包装，版本仅固化继续沿原联单流转的部分。 */
    public void snapshotInitialVersion(Manifest manifest) {
        if (versions.findEffectiveByManifest(manifest).isPresent()) {
            return;
        }
        Map<Integer, Integer> remainingPackages = remainingPackageCounts(manifest);
        Map<Integer, BigDecimal> remainingWeights = remainingItemWeights(manifest);
        ManifestVersion version = new ManifestVersion(manifest, 1,
                remainingDeclaredTotal(manifest), manifest.getReceivedWeight(), manifest.getFinalWeight());
        int seq = 1;
        for (ManifestItem item : manifest.getItems()) {
            version.addItem(new ManifestVersionItem(version, seq, item.getWasteCategory(),
                    remainingPackages.get(seq), remainingWeights.get(seq)));
            seq++;
        }
        versions.save(version);
    }

    /** 各明细继续流转的申报重量 = 原始重量 - 异常方案拆出重量。 */
    private Map<Integer, BigDecimal> remainingItemWeights(Manifest manifest) {
        Map<Integer, BigDecimal> detached = new HashMap<>();
        for (ManifestFlowAdjustment adjustment : flowAdjustments.findByManifestOrderByItemSeqAsc(manifest)) {
            detached.merge(adjustment.getItemSeq(), adjustment.getDetachedWeight(), BigDecimal::add);
        }
        Map<Integer, BigDecimal> remaining = new HashMap<>();
        int seq = 1;
        for (ManifestItem item : manifest.getItems()) {
            remaining.put(seq, item.getDeclaredWeight().subtract(detached.getOrDefault(seq, BigDecimal.ZERO)));
            seq++;
        }
        return remaining;
    }

    private BigDecimal latestConfirmWeight(List<ManifestEvent> timeline, DisputeConfirmRequest current,
                                           CustodianRole role, BigDecimal currentWeight) {
        BigDecimal latest = null;
        for (ManifestEvent event : timeline) {
            if (event.getType() == EventType.DISPUTE_CONFIRM && event.getRole() == role) {
                latest = event.getWeight();
            }
        }
        if (current.role() == role) {
            latest = currentWeight;
        }
        return latest;
    }

    private void rejectTimeReversal(List<ManifestEvent> timeline, Instant occurredAt) {
        if (!timeline.isEmpty()) {
            Instant last = timeline.get(timeline.size() - 1).getOccurredAt();
            if (occurredAt.isBefore(last)) {
                throw new BusinessRuleException("事件时间早于上一事件时间，拒绝时间倒序: " + occurredAt + " < " + last);
            }
        }
    }

    private void saveEvent(ManifestEvent event) {
        try {
            events.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("事件号已存在: " + event.getEventNo());
        }
    }

    private static EventType handoverTypeOf(CustodianRole role) {
        return switch (role) {
            case GENERATOR -> EventType.GENERATOR_SHIP;
            case TRANSPORTER -> EventType.TRANSPORTER_RECEIVE;
            case DISPOSER -> EventType.DISPOSER_RECEIVE;
        };
    }

    private static BigDecimal normalize(BigDecimal weight) {
        return weight.setScale(3, RoundingMode.UNNECESSARY);
    }

    public ManifestDetailResponse toDetail(Manifest manifest) {
        List<ManifestItemResponse> items = manifest.getItems().stream()
                .map(item -> new ManifestItemResponse(item.getWasteCategory(),
                        item.getPackageCount(), item.getDeclaredWeight()))
                .toList();
        return new ManifestDetailResponse(manifest.getManifestNo(), manifest.getGeneratorId(),
                manifest.getTransporterId(), manifest.getDisposerId(), manifest.getStatus(),
                manifest.getCurrentCustodian(), manifest.getDeclaredTotalWeight(),
                remainingDeclaredTotal(manifest),
                manifest.getReceivedWeight(), manifest.getFinalWeight(), items, manifest.getCreatedAt(),
                manifest.getCurrentVersionNo(), manifest.isRegulatoryFrozen(),
                packageFreezes.existsByManifestManifestNoAndStatus(
                        manifest.getManifestNo(), PackageFreezeStatus.FROZEN));
    }

    private static ManifestEventResponse toEventResponse(ManifestEvent event) {
        return new ManifestEventResponse(event.getEventNo(), event.getType(), event.getRole(),
                event.getWeight(), event.getOccurredAt(), event.getRecordedAt());
    }

    public static List<ManifestEventResponse> toEventResponses(List<ManifestEvent> events) {
        return events.stream()
                .map(ManifestService::toEventResponse)
                .toList();
    }
}
