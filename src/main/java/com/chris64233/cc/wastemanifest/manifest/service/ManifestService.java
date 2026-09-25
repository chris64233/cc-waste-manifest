package com.chris64233.cc.wastemanifest.manifest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.wastemanifest.manifest.domain.EventType;
import com.chris64233.cc.wastemanifest.manifest.domain.HandoverEvent;
import com.chris64233.cc.wastemanifest.manifest.domain.ManifestStatus;
import com.chris64233.cc.wastemanifest.manifest.domain.WasteItem;
import com.chris64233.cc.wastemanifest.manifest.domain.WasteManifest;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.EventSubmissionView;
import com.chris64233.cc.wastemanifest.manifest.dto.EventView;
import com.chris64233.cc.wastemanifest.manifest.dto.ItemView;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailView;
import com.chris64233.cc.wastemanifest.manifest.dto.SubmitEventRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.WasteItemRequest;
import com.chris64233.cc.wastemanifest.manifest.repo.HandoverEventRepository;
import com.chris64233.cc.wastemanifest.manifest.repo.WasteItemRepository;
import com.chris64233.cc.wastemanifest.manifest.repo.WasteManifestRepository;

@Service
public class ManifestService {

    /** 重量固定精度：3 位小数（千克）。 */
    static final int WEIGHT_SCALE = 3;

    private final WasteManifestRepository manifestRepository;
    private final WasteItemRepository itemRepository;
    private final HandoverEventRepository eventRepository;
    private final ManifestProperties properties;

    public ManifestService(WasteManifestRepository manifestRepository,
                           WasteItemRepository itemRepository,
                           HandoverEventRepository eventRepository,
                           ManifestProperties properties) {
        this.manifestRepository = manifestRepository;
        this.itemRepository = itemRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
    }

    @Transactional
    public ManifestDetailView createManifest(CreateManifestRequest request) {
        if (manifestRepository.existsByManifestNo(request.manifestNo())) {
            throw new ConflictException("联单号已存在: " + request.manifestNo());
        }

        BigDecimal tolerance = request.weightToleranceRatio() != null
                ? request.weightToleranceRatio()
                : properties.getWeightToleranceRatio();
        if (tolerance.signum() < 0 || tolerance.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidRequestException("允许差异比例必须在 0 到 1 之间");
        }
        tolerance = tolerance.setScale(4, RoundingMode.UNNECESSARY);

        BigDecimal totalWeight = BigDecimal.ZERO.setScale(WEIGHT_SCALE);
        for (WasteItemRequest item : request.items()) {
            totalWeight = totalWeight.add(normalizeWeight(item.declaredWeight(), "申报重量"));
        }

        WasteManifest manifest = new WasteManifest(
                request.manifestNo(),
                request.generatorParty(),
                request.carrierParty(),
                request.disposerParty(),
                totalWeight,
                tolerance);
        try {
            manifestRepository.saveAndFlush(manifest);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("联单号已存在: " + request.manifestNo());
        }

        int lineNo = 0;
        for (WasteItemRequest item : request.items()) {
            itemRepository.save(new WasteItem(manifest, ++lineNo, item.category(),
                    item.packageCount(), normalizeWeight(item.declaredWeight(), "申报重量")));
        }
        itemRepository.flush();
        return toDetailView(manifest);
    }

    @Transactional
    public EventSubmissionView submitEvent(String manifestNo, SubmitEventRequest request) {
        // 事件号全局唯一，先做幂等 / 冲突判定。
        HandoverEvent existing = eventRepository.findByEventNo(request.eventNo()).orElse(null);
        if (existing != null) {
            boolean sameContent = existing.getManifest().getManifestNo().equals(manifestNo)
                    && existing.getEventType() == request.eventType()
                    && existing.getActorParty().equals(request.actorParty())
                    && existing.getWeightValue().compareTo(normalizeWeight(request.weightValue(), "称重值")) == 0
                    && existing.getOccurredAt().equals(request.occurredAt());
            if (!sameContent) {
                throw new ConflictException("事件号已存在但内容不同: " + request.eventNo());
            }
            WasteManifest manifest = existing.getManifest();
            return new EventSubmissionView(true, toEventView(existing), toDetailView(manifest));
        }

        Long manifestId = manifestRepository.findIdByManifestNo(manifestNo)
                .orElseThrow(() -> new NotFoundException("联单不存在: " + manifestNo));

        // 悲观锁串行化同一联单上的并发事件；唯一约束兜底并发的同事件号插入。
        WasteManifest manifest;
        try {
            manifest = manifestRepository.findByIdForUpdate(manifestId).orElseThrow();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("联单正被其他交接操作更新，请重试");
        }

        // 加锁后再查一次事件号：并发提交同一事件号时，后来者识别为幂等重放而非状态冲突。
        existing = eventRepository.findByEventNo(request.eventNo()).orElse(null);
        if (existing != null) {
            boolean sameContent = existing.getManifest().getId().equals(manifestId)
                    && existing.getEventType() == request.eventType()
                    && existing.getActorParty().equals(request.actorParty())
                    && existing.getWeightValue().compareTo(normalizeWeight(request.weightValue(), "称重值")) == 0
                    && existing.getOccurredAt().equals(request.occurredAt());
            if (!sameContent) {
                throw new ConflictException("事件号已存在但内容不同: " + request.eventNo());
            }
            return new EventSubmissionView(true, toEventView(existing), toDetailView(manifest));
        }

        validateExpectedStage(manifest, request.eventType());
        validateActor(manifest, request.eventType(), request.actorParty());
        validateOccurredAt(manifest, request.occurredAt());

        BigDecimal weight = normalizeWeight(request.weightValue(), "称重值");
        int sequence = manifest.nextSequence();
        HandoverEvent event = new HandoverEvent(request.eventNo(), manifest, request.eventType(),
                request.actorParty(), weight, request.occurredAt(), sequence);
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("事件号已存在: " + request.eventNo());
        }

        applyEvent(manifest, request.eventType(), weight, request.occurredAt());
        manifestRepository.saveAndFlush(manifest);

        return new EventSubmissionView(false, toEventView(event), toDetailView(manifest));
    }

    @Transactional(readOnly = true)
    public ManifestDetailView getManifest(String manifestNo) {
        WasteManifest manifest = manifestRepository.findByManifestNo(manifestNo)
                .orElseThrow(() -> new NotFoundException("联单不存在: " + manifestNo));
        return toDetailView(manifest);
    }

    @Transactional(readOnly = true)
    public List<EventView> getTimeline(String manifestNo) {
        WasteManifest manifest = manifestRepository.findByManifestNo(manifestNo)
                .orElseThrow(() -> new NotFoundException("联单不存在: " + manifestNo));
        return eventRepository.findByManifestIdOrderBySequenceAsc(manifest.getId()).stream()
                .map(this::toEventView)
                .toList();
    }

    private void applyEvent(WasteManifest manifest, EventType type, BigDecimal weight,
                            java.time.Instant occurredAt) {
        switch (type) {
            case GENERATOR_HANDOVER -> {
                manifest.setGeneratorWeight(weight);
                // 承运方接货前仍由产生方承担保管责任，状态保持 CREATED。
            }
            case CARRIER_PICKUP -> {
                manifest.setCarrierWeight(weight);
                manifest.setStatus(ManifestStatus.IN_TRANSIT);
            }
            case DISPOSER_RECEIPT -> {
                manifest.setDisposerWeight(weight);
                if (withinTolerance(manifest, weight)) {
                    manifest.setResolvedWeight(weight);
                    manifest.setStatus(ManifestStatus.COMPLETED);
                } else {
                    manifest.setStatus(ManifestStatus.DISPUTED);
                }
            }
            case GENERATOR_CONFIRM, DISPOSER_CONFIRM -> resolveDispute(manifest, type, weight);
        }
        manifest.setLastEventAt(occurredAt);
    }

    /**
     * 争议解决：取两方各自最新一次确认重量，只有二者相等才完成；
     * 意见不一致（包括任一方尚未确认）保持争议，状态不被跳过。
     */
    private void resolveDispute(WasteManifest manifest, EventType type, BigDecimal weight) {
        BigDecimal generatorConfirmed = latestConfirmWeight(manifest.getId(), EventType.GENERATOR_CONFIRM);
        BigDecimal disposerConfirmed = latestConfirmWeight(manifest.getId(), EventType.DISPOSER_CONFIRM);
        if (type == EventType.GENERATOR_CONFIRM) {
            generatorConfirmed = weight;
        } else {
            disposerConfirmed = weight;
        }
        if (generatorConfirmed != null && disposerConfirmed != null
                && generatorConfirmed.compareTo(disposerConfirmed) == 0) {
            manifest.setResolvedWeight(generatorConfirmed);
            manifest.setStatus(ManifestStatus.COMPLETED);
        }
        // 不一致时保持 DISPUTED。
    }

    private BigDecimal latestConfirmWeight(Long manifestId, EventType type) {
        return eventRepository.findFirstByManifestIdAndEventTypeOrderBySequenceDesc(manifestId, type)
                .map(HandoverEvent::getWeightValue)
                .orElse(null);
    }

    private boolean withinTolerance(WasteManifest manifest, BigDecimal measuredWeight) {
        BigDecimal declared = manifest.getDeclaredTotalWeight();
        BigDecimal allowedDeviation = declared.multiply(manifest.getWeightToleranceRatio())
                .setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
        BigDecimal deviation = measuredWeight.subtract(declared).abs();
        return deviation.compareTo(allowedDeviation) <= 0;
    }

    private void validateExpectedStage(WasteManifest manifest, EventType type) {
        ManifestStatus status = manifest.getStatus();
        boolean valid = switch (type) {
            case GENERATOR_HANDOVER -> status == ManifestStatus.CREATED
                    && manifest.getGeneratorWeight() == null;
            case CARRIER_PICKUP -> status == ManifestStatus.CREATED
                    && manifest.getGeneratorWeight() != null;
            case DISPOSER_RECEIPT -> status == ManifestStatus.IN_TRANSIT;
            case GENERATOR_CONFIRM, DISPOSER_CONFIRM -> status == ManifestStatus.DISPUTED;
        };
        if (!valid) {
            throw new ConflictException("当前状态[" + status + "]不允许事件[" + type + "]，前序步骤未完成或联单已结束");
        }
    }

    private void validateActor(WasteManifest manifest, EventType type, String actorParty) {
        String expected = switch (type) {
            case GENERATOR_HANDOVER, GENERATOR_CONFIRM -> manifest.getGeneratorParty();
            case CARRIER_PICKUP -> manifest.getCarrierParty();
            case DISPOSER_RECEIPT, DISPOSER_CONFIRM -> manifest.getDisposerParty();
        };
        if (!expected.equals(actorParty)) {
            throw new ConflictException("提交角色与预期角色不符，应为: " + expected);
        }
    }

    private void validateOccurredAt(WasteManifest manifest, java.time.Instant occurredAt) {
        if (manifest.getLastEventAt() != null && !occurredAt.isAfter(manifest.getLastEventAt())) {
            throw new ConflictException("事件发生时间不得早于或等于上一事件时间");
        }
    }

    private BigDecimal normalizeWeight(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException(fieldName + "必须为正数");
        }
        if (value.stripTrailingZeros().scale() > WEIGHT_SCALE) {
            throw new InvalidRequestException(fieldName + "精度不能超过 " + WEIGHT_SCALE + " 位小数");
        }
        return value.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
    }

    private EventView toEventView(HandoverEvent event) {
        return new EventView(event.getSequence(), event.getEventNo(), event.getEventType().name(),
                event.getActorParty(), event.getWeightValue(), event.getOccurredAt(), event.getRecordedAt());
    }

    private ManifestDetailView toDetailView(WasteManifest manifest) {
        List<ItemView> items = itemRepository.findByManifestIdOrderByLineNoAsc(manifest.getId()).stream()
                .map(item -> new ItemView(item.getLineNo(), item.getCategory(),
                        item.getPackageCount(), item.getDeclaredWeight()))
                .toList();
        return new ManifestDetailView(
                manifest.getId(),
                manifest.getManifestNo(),
                manifest.getGeneratorParty(),
                manifest.getCarrierParty(),
                manifest.getDisposerParty(),
                manifest.getStatus().name(),
                manifest.getCurrentCustodian(),
                items,
                manifest.getDeclaredTotalWeight(),
                manifest.getWeightToleranceRatio(),
                manifest.getGeneratorWeight(),
                manifest.getCarrierWeight(),
                manifest.getDisposerWeight(),
                manifest.getResolvedWeight(),
                manifest.getLastEventAt(),
                manifest.getCreatedAt());
    }
}
