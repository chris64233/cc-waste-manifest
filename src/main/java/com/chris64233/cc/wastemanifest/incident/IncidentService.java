package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.incident.dto.AffectedLineRequest;
import com.chris64233.cc.wastemanifest.incident.dto.AffectedLineResponse;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentDecisionResponse;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentResponse;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.incident.dto.ResultLineRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentHandoverEventResponse;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentHandoverRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentItemResponse;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentResponse;
import com.chris64233.cc.wastemanifest.incident.exception.IncidentNotFoundException;
import com.chris64233.cc.wastemanifest.incident.exception.SegmentNotFoundException;
import com.chris64233.cc.wastemanifest.manifest.ActiveManifestOp;
import com.chris64233.cc.wastemanifest.manifest.ActiveOpType;
import com.chris64233.cc.wastemanifest.manifest.ActiveOpGuard;
import com.chris64233.cc.wastemanifest.manifest.Manifest;
import com.chris64233.cc.wastemanifest.manifest.ManifestEventRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestFlowAdjustment;
import com.chris64233.cc.wastemanifest.manifest.ManifestFlowAdjustmentRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestItem;
import com.chris64233.cc.wastemanifest.manifest.ManifestRepository;
import com.chris64233.cc.wastemanifest.manifest.ManifestStatus;
import com.chris64233.cc.wastemanifest.manifest.exception.BusinessRuleException;
import com.chris64233.cc.wastemanifest.manifest.exception.ConflictException;
import com.chris64233.cc.wastemanifest.manifest.exception.ManifestNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IncidentService {

    private final ManifestRepository manifests;
    private final ManifestEventRepository manifestEvents;
    private final TransportIncidentRepository incidents;
    private final IncidentDecisionRepository decisions;
    private final TransportSegmentRepository segments;
    private final SegmentHandoverEventRepository segmentEvents;
    private final PackageFreezeRepository freezes;
    private final ManifestFlowAdjustmentRepository flowAdjustments;
    private final ActiveOpGuard activeOps;

    public IncidentService(ManifestRepository manifests,
                           ManifestEventRepository manifestEvents,
                           TransportIncidentRepository incidents,
                           IncidentDecisionRepository decisions,
                           TransportSegmentRepository segments,
                           SegmentHandoverEventRepository segmentEvents,
                           PackageFreezeRepository freezes,
                           ManifestFlowAdjustmentRepository flowAdjustments,
                           ActiveOpGuard activeOps) {
        this.manifests = manifests;
        this.manifestEvents = manifestEvents;
        this.incidents = incidents;
        this.decisions = decisions;
        this.segments = segments;
        this.segmentEvents = segmentEvents;
        this.freezes = freezes;
        this.flowAdjustments = flowAdjustments;
        this.activeOps = activeOps;
    }

    // ---- 登记 ----

    @Transactional
    public IncidentResponse register(String manifestNo, RegisterIncidentRequest request) {
        Manifest manifest = lockManifest(manifestNo);

        TransportIncident existing = incidents.findByIncidentNo(request.incidentNo()).orElse(null);
        if (existing != null) {
            if (!existing.getManifest().getManifestNo().equals(manifestNo)) {
                throw new ConflictException("异常事件号已属于其他联单: " + request.incidentNo());
            }
            if (incidentContentMatches(existing, request)) {
                return toIncidentResponse(existing);
            }
            throw new ConflictException("异常事件号已存在且内容不一致: " + request.incidentNo());
        }

        if (manifest.getStatus() != ManifestStatus.IN_TRANSIT
                && manifest.getStatus() != ManifestStatus.RECEIVED_BY_TRANSPORTER) {
            throw new BusinessRuleException(
                    "仅运输中的当前有效联单可登记运输异常，当前状态: " + manifest.getStatus());
        }
        if (manifest.isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，不得登记运输异常");
        }

        validateTypeSpecificFields(request);
        if (request.resultLines() != null && request.resultLines().size() != request.affectedLines().size()) {
            throw new BusinessRuleException("处置口径行数必须与受影响包装行数一致");
        }

        // 异常发生时间不得早于联单已有交接事件（拒绝时间倒序）。
        List<com.chris64233.cc.wastemanifest.manifest.ManifestEvent> manifestEventList =
                manifestEvents.findByManifestOrderByIdAsc(manifest);
        if (!manifestEventList.isEmpty()
                && request.occurredAt().isBefore(
                        manifestEventList.get(manifestEventList.size() - 1).getOccurredAt())) {
            throw new BusinessRuleException("异常发生时间早于上一交接事件时间，拒绝时间倒序");
        }

        // 累计此前方案已拆出的包装，校验受影响数量不超过继续流转的可用量。
        Map<Integer, Integer> detachedPackages = new HashMap<>();
        Map<Integer, BigDecimal> detachedWeights = new HashMap<>();
        for (ManifestFlowAdjustment adjustment : flowAdjustments.findByManifestOrderByItemSeqAsc(manifest)) {
            detachedPackages.merge(adjustment.getItemSeq(), adjustment.getDetachedPackageCount(), Integer::sum);
            detachedWeights.merge(adjustment.getItemSeq(), adjustment.getDetachedWeight(), BigDecimal::add);
        }

        Map<Integer, ManifestItem> itemsBySeq = new HashMap<>();
        int seq = 1;
        for (ManifestItem item : manifest.getItems()) {
            itemsBySeq.put(seq++, item);
        }

        Set<Integer> seenSeq = new HashSet<>();
        List<LineValues> lines = new ArrayList<>();
        boolean regulatorRequired = request.type() == IncidentType.LOSS;
        for (int i = 0; i < request.affectedLines().size(); i++) {
            AffectedLineRequest affected = request.affectedLines().get(i);
            if (!seenSeq.add(affected.itemSeq())) {
                throw new BusinessRuleException("同一明细在一笔异常中只能出现一次: " + affected.itemSeq());
            }
            ManifestItem item = itemsBySeq.get(affected.itemSeq());
            if (item == null) {
                throw new BusinessRuleException("明细序号不存在: " + affected.itemSeq());
            }
            int affectedCount = affected.packageCount();
            BigDecimal affectedWeight = normalize(affected.weight());
            int availableCount = item.getPackageCount() - detachedPackages.getOrDefault(affected.itemSeq(), 0);
            BigDecimal availableWeight = item.getDeclaredWeight()
                    .subtract(detachedWeights.getOrDefault(affected.itemSeq(), BigDecimal.ZERO));
            if (affectedCount > availableCount) {
                throw new BusinessRuleException(
                        "受影响包装数超过明细继续流转的可用包装数: 明细" + affected.itemSeq()
                                + " 可用 " + availableCount + "，申请 " + affectedCount);
            }
            if (affectedWeight.compareTo(availableWeight) > 0) {
                throw new BusinessRuleException(
                        "受影响重量超过明细继续流转的可用重量: 明细" + affected.itemSeq()
                                + " 可用 " + availableWeight + "，申请 " + affectedWeight);
            }

            Integer resultCount = affectedCount;
            BigDecimal resultWeight = affectedWeight;
            String resultCategory = item.getWasteCategory();
            if (request.type() == IncidentType.LOSS) {
                resultCount = 0;
                resultWeight = BigDecimal.ZERO.setScale(3);
            } else if (request.resultLines() != null) {
                ResultLineRequest result = request.resultLines().get(i);
                if (result.packageCount() != null) {
                    resultCount = result.packageCount();
                }
                if (result.weight() != null) {
                    resultWeight = normalize(result.weight());
                }
                if (result.wasteCategory() != null && !result.wasteCategory().isBlank()) {
                    resultCategory = result.wasteCategory().trim();
                }
                if (resultCount <= 0 || resultWeight.signum() <= 0) {
                    throw new BusinessRuleException(
                            "新运输段的处置口径包装数与重量必须为正（全部损失应登记为遗失）");
                }
            }
            if (resultCount != affectedCount
                    || resultWeight.compareTo(affectedWeight) != 0
                    || !resultCategory.equals(item.getWasteCategory())) {
                regulatorRequired = true;
            }
            lines.add(new LineValues(affected.itemSeq(), item.getWasteCategory(),
                    affectedCount, affectedWeight, resultCount, resultWeight, resultCategory));
        }

        // 共享活动守卫：与差错更正互斥，同一版本并发只有一个操作登记成功。
        if (!activeOps.tryAcquire(manifestNo, ActiveOpType.INCIDENT,
                request.incidentNo(), manifest.getCurrentVersionNo())) {
            throw new BusinessRuleException("同一联单同时只能有一个活动操作（更正或异常处置）");
        }

        TransportIncident incident = new TransportIncident(request.incidentNo(), manifest, request.type(),
                manifest.getCurrentVersionNo(), request.occurredAt(), request.location(),
                request.evidenceRef(),
                request.type() == IncidentType.DIVERSION ? request.newDisposerId() : null,
                request.type() == IncidentType.DIVERSION ? request.estimatedArrivalAt() : null,
                regulatorRequired);
        for (LineValues line : lines) {
            incident.addAffectedLine(new IncidentAffectedLine(incident, line.itemSeq(), line.category(),
                    line.affectedCount(), line.affectedWeight(),
                    line.resultCount(), line.resultWeight(), line.resultCategory()));
        }
        try {
            incidents.saveAndFlush(incident);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("异常事件号冲突: " + request.incidentNo());
        }

        // 泄漏/遗失登记即冻结受影响包装，冻结期间不能继续正常交接。
        if (request.type() == IncidentType.LEAK || request.type() == IncidentType.LOSS) {
            for (LineValues line : lines) {
                freezes.save(new PackageFreeze(manifest, request.incidentNo(),
                        line.itemSeq(), line.affectedCount()));
            }
            freezes.flush();
        }
        return toIncidentResponse(incident);
    }

    // ---- 决定 ----

    /**
     * 处置决定。终止性标记（拒绝/撤回/冻结/失效）需要连同冻结释放一起落库，
     * 因此业务规则异常不回滚；冲突异常仍整体回滚。
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public IncidentResponse decide(String manifestNo, String incidentNo, IncidentDecisionRequest request) {
        lockManifest(manifestNo);
        TransportIncident incident = loadIncident(manifestNo, incidentNo);

        var existing = decisions.findByDecisionNo(request.decisionNo());
        if (existing.isPresent()) {
            IncidentDecision decision = existing.get();
            if (decision.matches(incidentNo, request.role(), request.value(), request.occurredAt())
                    && decision.getIncident().getManifest().getManifestNo().equals(manifestNo)) {
                return toIncidentResponse(decision.getIncident());
            }
            throw new ConflictException("决定事件号已存在且内容不一致: " + request.decisionNo());
        }

        if (!incident.isPending()) {
            throw new BusinessRuleException("异常处置已结束（" + incident.getStatus() + "），不能再提交决定");
        }

        if (incident.getManifest().isRegulatoryFrozen()) {
            terminate(incident, IncidentStatus.FROZEN);
            throw new BusinessRuleException("联单已被监管冻结，异常处置 " + incidentNo + " 终止、不得落地");
        }
        if (incident.getBaseVersionNo() != incident.getManifest().getCurrentVersionNo()) {
            terminate(incident, IncidentStatus.SUPERSEDED);
            throw new BusinessRuleException(
                    "引用的联单版本已变化，异常处置 " + incidentNo + " 必须基于新版本重新登记");
        }

        Set<IncidentRole> requiredRoles = requiredRoles(incident);
        if (!requiredRoles.contains(request.role())) {
            throw new BusinessRuleException("该角色按本次异常责任无需确认: " + request.role());
        }
        for (IncidentDecision decision : incident.getDecisions()) {
            if (decision.getRole() == request.role()) {
                throw new BusinessRuleException("该角色已对本次异常处置作出决定: " + request.role());
            }
            if (request.occurredAt().isBefore(decision.getOccurredAt())) {
                throw new BusinessRuleException(
                        "决定时间早于已有决定时间，拒绝时间倒序: " + request.occurredAt()
                                + " < " + decision.getOccurredAt());
            }
        }
        if (request.value() == IncidentDecisionValue.WITHDRAWN && request.role() != IncidentRole.TRANSPORTER) {
            throw new BusinessRuleException("仅登记异常的承运方可以撤回处置方案");
        }

        IncidentDecision decision = new IncidentDecision(request.decisionNo(), incident,
                request.role(), request.value(), request.occurredAt());
        try {
            decisions.saveAndFlush(decision);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("决定事件号已存在: " + request.decisionNo());
        }
        incident.addDecision(decision);

        switch (request.value()) {
            case REJECTED -> terminate(incident, IncidentStatus.REJECTED);
            case WITHDRAWN -> terminate(incident, IncidentStatus.WITHDRAWN);
            case APPROVED -> {
                if (allRequiredApproved(incident, requiredRoles)) {
                    makeEffective(incident);
                }
            }
        }
        return toIncidentResponse(incident);
    }

    // ---- 生效：一次性拆分 ----

    private void makeEffective(TransportIncident incident) {
        Manifest manifest = incident.getManifest();
        // 生效前在联单行锁下再次复核：版本、冻结、活动守卫。
        if (incident.getBaseVersionNo() != manifest.getCurrentVersionNo()) {
            terminate(incident, IncidentStatus.SUPERSEDED);
            throw new BusinessRuleException("引用的联单版本已变化，异常处置不得落地");
        }
        if (manifest.isRegulatoryFrozen()) {
            terminate(incident, IncidentStatus.FROZEN);
            throw new BusinessRuleException("联单已被监管冻结，异常处置不得落地");
        }
        ActiveManifestOp guard = activeOps.find(manifest.getManifestNo());
        if (guard == null || guard.getOpType() != ActiveOpType.INCIDENT
                || !guard.getRefNo().equals(incident.getIncidentNo())) {
            throw new IllegalStateException("活动操作守卫缺失或指向其他操作，异常处置不得落地");
        }

        int seqNo = segments.findByParentManifestManifestNoOrderBySeqNoAsc(manifest.getManifestNo()).size() + 1;
        String segmentNo = incident.getIncidentNo() + "-S" + seqNo;

        SegmentType segmentType = incident.getType() == IncidentType.LOSS
                ? SegmentType.LOSS : SegmentType.NEW_TRANSPORT;
        String destination = incident.getType() == IncidentType.DIVERSION
                ? incident.getNewDisposerId() : manifest.getDisposerId();
        TransportSegment segment = new TransportSegment(segmentNo, incident, manifest, seqNo,
                segmentType, destination, incident.getEstimatedArrivalAt(), BigDecimal.ZERO.setScale(3));

        // 先构建全部段明细与流向调整，任一数量对不平则整体抛错回滚，不留半条运输链。
        List<SegmentItem> segmentItems = new ArrayList<>();
        List<ManifestFlowAdjustment> adjustments = new ArrayList<>();
        BigDecimal segmentWeight = BigDecimal.ZERO.setScale(3);
        for (IncidentAffectedLine line : incident.getAffectedLines()) {
            int segmentPackageCount;
            BigDecimal segmentItemWeight;
            String segmentCategory;
            if (segmentType == SegmentType.LOSS) {
                // 损失记录登记损失口径（受影响原始量），包装退出运输链
                segmentPackageCount = line.getAffectedPackageCount();
                segmentItemWeight = line.getAffectedWeight();
                segmentCategory = line.getWasteCategory();
            } else {
                segmentPackageCount = line.getResultPackageCount();
                segmentItemWeight = line.getResultWeight();
                segmentCategory = line.getResultWasteCategory();
            }
            segmentItems.add(new SegmentItem(segment, line.getItemSeq(), segmentCategory,
                    segmentPackageCount, segmentItemWeight));
            segmentWeight = segmentWeight.add(segmentItemWeight);
            adjustments.add(new ManifestFlowAdjustment(manifest, incident.getIncidentNo(),
                    line.getItemSeq(), line.getAffectedPackageCount(), line.getAffectedWeight()));
        }

        // 一次性落库：段明细 + 原联单拆减 + 冻结释放 + 状态置生效 + 守卫释放。
        segment.initDeclaredWeight(segmentWeight);
        segmentItems.forEach(segment::addItem);
        segments.saveAndFlush(segment);
        adjustments.forEach(flowAdjustments::save);
        flowAdjustments.flush();
        incident.addSegment(segment);
        releaseFreezes(incident.getIncidentNo());
        // 全部包装转出（全改道/全损失）时，原联单无剩余包装继续流转，予以结案。
        if (allPackagesDetached(manifest)) {
            manifest.markClosed();
        }
        incident.markEffective();
        incidents.saveAndFlush(incident);
        activeOps.release(manifest.getManifestNo(), incident.getIncidentNo());
    }

    /** 原联单每个明细的包装都已被异常方案拆出。 */
    private boolean allPackagesDetached(Manifest manifest) {
        Map<Integer, Integer> detached = new HashMap<>();
        for (ManifestFlowAdjustment adjustment : flowAdjustments.findByManifestOrderByItemSeqAsc(manifest)) {
            detached.merge(adjustment.getItemSeq(), adjustment.getDetachedPackageCount(), Integer::sum);
        }
        int seq = 1;
        for (ManifestItem item : manifest.getItems()) {
            if (detached.getOrDefault(seq, 0) < item.getPackageCount()) {
                return false;
            }
            seq++;
        }
        return true;
    }

    /** 处置终止：拒绝/撤回/冻结/失效，释放冻结包装与活动守卫，不产生任何运输段。 */
    private void terminate(TransportIncident incident, IncidentStatus status) {
        switch (status) {
            case REJECTED -> incident.markRejected();
            case WITHDRAWN -> incident.markWithdrawn();
            case FROZEN -> incident.markFrozen();
            case SUPERSEDED -> incident.markSuperseded();
            default -> throw new IllegalArgumentException("非终止状态: " + status);
        }
        incidents.saveAndFlush(incident);
        releaseFreezes(incident.getIncidentNo());
        activeOps.release(incident.getManifest().getManifestNo(), incident.getIncidentNo());
    }

    private void releaseFreezes(String incidentNo) {
        for (PackageFreeze freeze : freezes.findByIncidentNo(incidentNo)) {
            freeze.release();
        }
        freezes.flush();
    }

    // ---- 新运输段交接 ----

    @Transactional
    public SegmentResponse segmentHandover(String manifestNo, String segmentNo, SegmentHandoverRequest request) {
        lockManifest(manifestNo);
        TransportSegment segment = segments.findBySegmentNoForUpdate(segmentNo)
                .orElseThrow(() -> new SegmentNotFoundException(segmentNo));
        if (!segment.getParentManifest().getManifestNo().equals(manifestNo)) {
            throw new SegmentNotFoundException(segmentNo);
        }
        if (segment.getType() == SegmentType.LOSS) {
            throw new BusinessRuleException("损失记录不进行交接");
        }
        if (segment.getParentManifest().isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，运输段交接暂停");
        }

        var existing = segmentEvents.findByEventNo(request.eventNo());
        if (existing.isPresent()) {
            SegmentHandoverEvent event = existing.get();
            SegmentEventType replayType = switch (request.role()) {
                case TRANSPORTER -> SegmentEventType.TRANSPORTER_DELIVER;
                case DISPOSER -> SegmentEventType.DISPOSER_RECEIVE;
                case GENERATOR -> throw new BusinessRuleException("运输段交接不涉及产生方");
            };
            if (event.getSegment().getSegmentNo().equals(segmentNo)
                    && event.getType() == replayType
                    && event.getWeight().compareTo(request.weight()) == 0
                    && event.getOccurredAt().equals(request.occurredAt())) {
                return toSegmentResponse(segment);
            }
            throw new ConflictException("交接事件号已存在且内容不一致: " + request.eventNo());
        }

        BigDecimal weight = normalize(request.weight());
        List<SegmentHandoverEvent> timeline = segmentEvents.findBySegmentOrderByIdAsc(segment);
        SegmentEventType type;
        switch (segment.getStatus()) {
            case IN_TRANSIT -> {
                if (request.role() != com.chris64233.cc.wastemanifest.manifest.CustodianRole.TRANSPORTER) {
                    throw new BusinessRuleException("运输段当前应由承运方交付，实际为 " + request.role());
                }
                type = SegmentEventType.TRANSPORTER_DELIVER;
            }
            case DELIVERED -> {
                if (request.role() != com.chris64233.cc.wastemanifest.manifest.CustodianRole.DISPOSER) {
                    throw new BusinessRuleException("运输段当前应由处置方接收，实际为 " + request.role());
                }
                type = SegmentEventType.DISPOSER_RECEIVE;
            }
            default -> throw new BusinessRuleException("运输段当前状态不允许交接: " + segment.getStatus());
        }
        if (!timeline.isEmpty() && request.occurredAt().isBefore(timeline.get(timeline.size() - 1).getOccurredAt())) {
            throw new BusinessRuleException("事件时间早于上一事件时间，拒绝时间倒序");
        }

        SegmentHandoverEvent event = new SegmentHandoverEvent(request.eventNo(), segment, type,
                weight, request.occurredAt());
        try {
            segmentEvents.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("交接事件号已存在: " + request.eventNo());
        }
        segment.addHandoverEvent(event);
        if (type == SegmentEventType.TRANSPORTER_DELIVER) {
            segment.markDelivered();
        } else {
            segment.markCompleted(weight);
        }
        segments.saveAndFlush(segment);
        return toSegmentResponse(segment);
    }

    // ---- 查询 ----

    /**
     * 监管冻结时点：该联单所有确认中的异常处置一并终止为 FROZEN，
     * 释放冻结包装与活动守卫，不产生任何运输段。
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void freezePending(String manifestNo) {
        for (TransportIncident incident : incidents.findByManifestManifestNoOrderByIdAsc(manifestNo)) {
            if (incident.isPending()) {
                terminate(incident, IncidentStatus.FROZEN);
            }
        }
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(String manifestNo, String incidentNo) {
        return toIncidentResponse(loadIncident(manifestNo, incidentNo));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents(String manifestNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
        return incidents.findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(IncidentService::toIncidentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SegmentResponse> listSegments(String manifestNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
        return segments.findByParentManifestManifestNoOrderBySeqNoAsc(manifestNo).stream()
                .map(IncidentService::toSegmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SegmentResponse getSegment(String manifestNo, String segmentNo) {
        TransportSegment segment = segments.findBySegmentNo(segmentNo)
                .orElseThrow(() -> new SegmentNotFoundException(segmentNo));
        if (!segment.getParentManifest().getManifestNo().equals(manifestNo)) {
            throw new SegmentNotFoundException(segmentNo);
        }
        return toSegmentResponse(segment);
    }

    // ---- 校验辅助 ----

    private Set<IncidentRole> requiredRoles(TransportIncident incident) {
        EnumSet<IncidentRole> roles = EnumSet.of(
                IncidentRole.GENERATOR, IncidentRole.TRANSPORTER, IncidentRole.OLD_DISPOSER);
        if (incident.getType() == IncidentType.DIVERSION) {
            roles.add(IncidentRole.NEW_DISPOSER);
        }
        if (incident.isRegulatorRequired()) {
            roles.add(IncidentRole.REGULATOR);
        }
        return roles;
    }

    private boolean allRequiredApproved(TransportIncident incident, Set<IncidentRole> requiredRoles) {
        Set<IncidentRole> approved = EnumSet.noneOf(IncidentRole.class);
        for (IncidentDecision decision : incident.getDecisions()) {
            if (decision.getValue() == IncidentDecisionValue.APPROVED) {
                approved.add(decision.getRole());
            }
        }
        return approved.containsAll(requiredRoles);
    }

    private void validateTypeSpecificFields(RegisterIncidentRequest request) {
        if (request.type() == IncidentType.DIVERSION) {
            if (request.newDisposerId() == null || request.newDisposerId().isBlank()) {
                throw new BusinessRuleException("改道必须指定新的处置方");
            }
            if (request.estimatedArrivalAt() == null) {
                throw new BusinessRuleException("改道必须指定预计到达时间");
            }
        } else if (request.newDisposerId() != null || request.estimatedArrivalAt() != null) {
            throw new BusinessRuleException("仅改道异常可指定新处置方与预计到达时间");
        }
    }

    /** 幂等匹配：联单、类型、时间、地点、证据、改道目的地与全部受影响/处置口径行一致。 */
    private boolean incidentContentMatches(TransportIncident incident, RegisterIncidentRequest request) {
        if (incident.getType() != request.type()
                || !incident.getOccurredAt().equals(request.occurredAt())
                || !incident.getLocation().equals(request.location())
                || !incident.getEvidenceRef().equals(request.evidenceRef())) {
            return false;
        }
        String expectedNewDisposer = request.type() == IncidentType.DIVERSION ? request.newDisposerId() : null;
        Instant expectedEta = request.type() == IncidentType.DIVERSION ? request.estimatedArrivalAt() : null;
        if (!java.util.Objects.equals(incident.getNewDisposerId(), expectedNewDisposer)
                || !java.util.Objects.equals(incident.getEstimatedArrivalAt(), expectedEta)) {
            return false;
        }
        if (incident.getAffectedLines().size() != request.affectedLines().size()) {
            return false;
        }
        for (int i = 0; i < request.affectedLines().size(); i++) {
            IncidentAffectedLine saved = incident.getAffectedLines().get(i);
            AffectedLineRequest asked = request.affectedLines().get(i);
            if (saved.getItemSeq() != asked.itemSeq()
                    || saved.getAffectedPackageCount() != asked.packageCount()
                    || saved.getAffectedWeight().compareTo(normalize(asked.weight())) != 0) {
                return false;
            }
            Integer askedResultCount = asked.packageCount();
            BigDecimal askedResultWeight = normalize(asked.weight());
            String askedResultCategory = saved.getWasteCategory();
            if (request.type() == IncidentType.LOSS) {
                askedResultCount = 0;
                askedResultWeight = BigDecimal.ZERO.setScale(3);
            } else if (request.resultLines() != null) {
                ResultLineRequest result = request.resultLines().get(i);
                if (result.packageCount() != null) {
                    askedResultCount = result.packageCount();
                }
                if (result.weight() != null) {
                    askedResultWeight = normalize(result.weight());
                }
                if (result.wasteCategory() != null && !result.wasteCategory().isBlank()) {
                    askedResultCategory = result.wasteCategory().trim();
                }
            }
            if (saved.getResultPackageCount() == null
                    || saved.getResultPackageCount() != askedResultCount
                    || saved.getResultWeight() == null
                    || saved.getResultWeight().compareTo(askedResultWeight) != 0
                    || !java.util.Objects.equals(saved.getResultWasteCategory(), askedResultCategory)) {
                return false;
            }
        }
        return true;
    }

    private boolean regulatorRequired(RegisterIncidentRequest request) {
        if (request.type() == IncidentType.LOSS) {
            return true;
        }
        if (request.resultLines() == null) {
            return false;
        }
        for (int i = 0; i < request.resultLines().size(); i++) {
            ResultLineRequest result = request.resultLines().get(i);
            AffectedLineRequest affected = request.affectedLines().get(i);
            if ((result.packageCount() != null && result.packageCount() != affected.packageCount())
                    || (result.weight() != null
                            && normalize(result.weight()).compareTo(normalize(affected.weight())) != 0)
                    || (result.wasteCategory() != null && !result.wasteCategory().isBlank())) {
                return true;
            }
        }
        return false;
    }

    private Manifest lockManifest(String manifestNo) {
        return manifests.findByManifestNoForUpdate(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
    }

    private TransportIncident loadIncident(String manifestNo, String incidentNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
        TransportIncident incident = incidents.findByIncidentNo(incidentNo)
                .orElseThrow(() -> new IncidentNotFoundException(incidentNo));
        if (!incident.getManifest().getManifestNo().equals(manifestNo)) {
            throw new IncidentNotFoundException(incidentNo);
        }
        return incident;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    // ---- 映射 ----

    public static IncidentResponse toIncidentResponse(TransportIncident incident) {
        List<AffectedLineResponse> lineResponses = incident.getAffectedLines().stream()
                .map(l -> new AffectedLineResponse(l.getItemSeq(), l.getWasteCategory(),
                        l.getAffectedPackageCount(), l.getAffectedWeight(),
                        l.getResultPackageCount(), l.getResultWeight(), l.getResultWasteCategory()))
                .toList();
        List<IncidentDecisionResponse> decisionResponses = incident.getDecisions().stream()
                .map(d -> new IncidentDecisionResponse(d.getDecisionNo(), d.getRole(),
                        d.getValue(), d.getOccurredAt(), d.getRecordedAt()))
                .toList();
        List<SegmentResponse> segmentResponses = incident.getSegments().stream()
                .map(IncidentService::toSegmentResponse)
                .toList();
        return new IncidentResponse(incident.getIncidentNo(),
                incident.getManifest().getManifestNo(), incident.getType(), incident.getStatus(),
                incident.getBaseVersionNo(), incident.getOccurredAt(), incident.getLocation(),
                incident.getEvidenceRef(), incident.getNewDisposerId(), incident.getEstimatedArrivalAt(),
                incident.isRegulatorRequired(), lineResponses, decisionResponses, segmentResponses,
                incident.getCreatedAt(), incident.getClosedAt());
    }

    public static SegmentResponse toSegmentResponse(TransportSegment segment) {
        List<SegmentItemResponse> itemResponses = segment.getItems().stream()
                .map(i -> new SegmentItemResponse(i.getSourceItemSeq(), i.getWasteCategory(),
                        i.getPackageCount(), i.getDeclaredWeight()))
                .toList();
        List<SegmentHandoverEventResponse> eventResponses = segment.getHandoverEvents().stream()
                .map(e -> new SegmentHandoverEventResponse(e.getEventNo(), e.getType(),
                        e.getWeight(), e.getOccurredAt(), e.getRecordedAt()))
                .toList();
        return new SegmentResponse(segment.getSegmentNo(), segment.getIncident().getIncidentNo(),
                segment.getParentManifest().getManifestNo(), segment.getSeqNo(),
                segment.getType().name(), segment.getDestinationDisposerId(),
                segment.getEstimatedArrivalAt(), segment.getStatus(),
                segment.getCurrentCustodian().name(), segment.getDeclaredWeight(),
                segment.getReceivedWeight(), itemResponses, eventResponses,
                segment.getEffectiveAt(), segment.getCompletedAt());
    }

    private record LineValues(int itemSeq, String category, int affectedCount, BigDecimal affectedWeight,
                              int resultCount, BigDecimal resultWeight, String resultCategory) {
    }
}
