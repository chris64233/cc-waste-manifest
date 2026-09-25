package com.chris64233.cc.wastemanifest.manifest;

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
import java.util.List;

@Service
public class ManifestService {

    private final ManifestRepository manifests;
    private final ManifestEventRepository events;
    private final BigDecimal weightToleranceRatio;

    public ManifestService(ManifestRepository manifests,
                           ManifestEventRepository events,
                           @Value("${manifest.weight-tolerance-ratio:0.05}") BigDecimal weightToleranceRatio) {
        this.manifests = manifests;
        this.events = events;
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
            case WEIGHT_DISPUTE, COMPLETED ->
                    throw new BusinessRuleException("当前状态不允许交接: " + manifest.getStatus());
        };
        if (request.role() != expectedRole) {
            throw new BusinessRuleException("交接顺序错误，当前应由 " + expectedRole + " 提交，实际为 " + request.role());
        }

        List<ManifestEvent> timeline = events.findByManifestOrderByIdAsc(manifest);
        rejectTimeReversal(timeline, request.occurredAt());

        saveEvent(new ManifestEvent(manifest, request.eventNo(), type, request.role(), weight, request.occurredAt()));
        switch (type) {
            case GENERATOR_SHIP -> manifest.markInTransit();
            case TRANSPORTER_RECEIVE -> manifest.markReceivedByTransporter();
            case DISPOSER_RECEIVE -> settleByWeight(manifest, weight);
            default -> throw new IllegalStateException("非交接事件类型: " + type);
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
        BigDecimal declared = manifest.getDeclaredTotalWeight();
        BigDecimal diff = receivedWeight.subtract(declared).abs();
        BigDecimal allowed = declared.multiply(weightToleranceRatio);
        if (diff.compareTo(allowed) <= 0) {
            manifest.markCompleted(receivedWeight, receivedWeight);
        } else {
            manifest.markWeightDispute(receivedWeight);
        }
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

    private static ManifestDetailResponse toDetail(Manifest manifest) {
        List<ManifestItemResponse> items = manifest.getItems().stream()
                .map(item -> new ManifestItemResponse(item.getWasteCategory(),
                        item.getPackageCount(), item.getDeclaredWeight()))
                .toList();
        return new ManifestDetailResponse(manifest.getManifestNo(), manifest.getGeneratorId(),
                manifest.getTransporterId(), manifest.getDisposerId(), manifest.getStatus(),
                manifest.getCurrentCustodian(), manifest.getDeclaredTotalWeight(),
                manifest.getReceivedWeight(), manifest.getFinalWeight(), items, manifest.getCreatedAt());
    }

    private static ManifestEventResponse toEventResponse(ManifestEvent event) {
        return new ManifestEventResponse(event.getEventNo(), event.getType(), event.getRole(),
                event.getWeight(), event.getOccurredAt(), event.getRecordedAt());
    }
}
