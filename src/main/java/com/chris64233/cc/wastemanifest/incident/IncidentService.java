package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionRepository;
import com.chris64233.cc.wastemanifest.correction.CorrectionStatus;
import com.chris64233.cc.wastemanifest.correction.DecisionValue;
import com.chris64233.cc.wastemanifest.correction.ManifestVersion;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionItem;
import com.chris64233.cc.wastemanifest.correction.ManifestVersionRepository;
import com.chris64233.cc.wastemanifest.correction.dto.ManifestRecordResponse;
import com.chris64233.cc.wastemanifest.incident.dto.AffectedPackageRequest;
import com.chris64233.cc.wastemanifest.incident.dto.AffectedPackageResponse;
import com.chris64233.cc.wastemanifest.incident.dto.CreatePlanRequest;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentRecordResponse;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentResponse;
import com.chris64233.cc.wastemanifest.incident.dto.LossRecordResponse;
import com.chris64233.cc.wastemanifest.incident.dto.PackageSplitResponse;
import com.chris64233.cc.wastemanifest.incident.dto.PlanDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.PlanDecisionResponse;
import com.chris64233.cc.wastemanifest.incident.dto.PlanResponse;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentEventRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentEventResponse;
import com.chris64233.cc.wastemanifest.incident.dto.TransportSegmentResponse;
import com.chris64233.cc.wastemanifest.incident.exception.IncidentNotFoundException;
import com.chris64233.cc.wastemanifest.incident.exception.PlanNotFoundException;
import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.Manifest;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IncidentService {

    private final ManifestRepository manifests;
    private final ManifestVersionRepository versions;
    private final CorrectionRepository corrections;
    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final IncidentPlanDecisionRepository decisions;
    private final IncidentPackageSplitRepository splits;
    private final TransportSegmentRepository segments;
    private final SegmentEventRepository segmentEvents;
    private final LossRecordRepository losses;

    public IncidentService(ManifestRepository manifests,
                           ManifestVersionRepository versions,
                           CorrectionRepository corrections,
                           IncidentRepository incidents,
                           IncidentPlanRepository plans,
                           IncidentPlanDecisionRepository decisions,
                           IncidentPackageSplitRepository splits,
                           TransportSegmentRepository segments,
                           SegmentEventRepository segmentEvents,
                           LossRecordRepository losses) {
        this.manifests = manifests;
        this.versions = versions;
        this.corrections = corrections;
        this.incidents = incidents;
        this.plans = plans;
        this.decisions = decisions;
        this.splits = splits;
        this.segments = segments;
        this.segmentEvents = segmentEvents;
        this.losses = losses;
    }

    // ---- 异常登记 ----

    @Transactional
    public IncidentResponse register(String manifestNo, RegisterIncidentRequest request) {
        Manifest manifest = lockManifest(manifestNo);

        Incident existing = incidents.findByEventNo(request.eventNo()).orElse(null);
        if (existing != null) {
            if (incidentContentMatches(existing, manifestNo, request)) {
                return toIncidentResponse(existing);
            }
            throw new ConflictException("异常事件号已存在且内容不一致: " + request.eventNo());
        }

        // 仅运输途中（承运方保管期间）可登记异常。
        if (manifest.getStatus() != ManifestStatus.IN_TRANSIT
                && manifest.getStatus() != ManifestStatus.RECEIVED_BY_TRANSPORTER) {
            throw new BusinessRuleException("仅运输中的联单可登记运输异常，当前状态: " + manifest.getStatus());
        }
        if (manifest.getCurrentCustodian() != CustodianRole.TRANSPORTER) {
            throw new BusinessRuleException("货物不在承运方保管中，不能登记运输异常");
        }
        if (manifest.isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，不得登记运输异常");
        }
        if (incidents.existsByManifestManifestNoAndStatus(manifestNo, IncidentStatus.OPEN)) {
            throw new BusinessRuleException("同一联单存在未处置完成的异常，需先一次性处置完毕");
        }
        if (corrections.existsByManifestManifestNoAndStatus(manifestNo, CorrectionStatus.PENDING)) {
            throw new BusinessRuleException("联单存在确认中的更正，异常登记必须基于新版本重新计算");
        }

        Map<Integer, PackageSnapshot> current = currentPackages(manifest);
        Map<Integer, Integer> requested = new LinkedHashMap<>();
        List<AffectedPackageRequest> affected = request.affectedPackages();
        for (AffectedPackageRequest p : affected) {
            PackageSnapshot snapshot = current.get(p.itemSeq());
            if (snapshot == null) {
                throw new BusinessRuleException("明细序号不存在: " + p.itemSeq());
            }
            Integer prev = requested.putIfAbsent(p.itemSeq(), p.quantity());
            if (prev != null) {
                throw new BusinessRuleException("同一明细的受影响包装只能登记一次: " + p.itemSeq());
            }
            if (p.quantity() > snapshot.packageCount()) {
                throw new BusinessRuleException(
                        "受影响包装数量超过该明细包装总数: 明细 " + p.itemSeq()
                                + "，总数 " + snapshot.packageCount() + "，受影响 " + p.quantity());
            }
        }

        Incident incident = new Incident(request.eventNo(), manifest, request.type(), request.occurredAt(),
                request.location(), request.evidenceRef(), manifest.getCurrentVersionNo());
        for (AffectedPackageRequest p : affected) {
            incident.addAffectedPackage(new IncidentAffectedPackage(incident, p.itemSeq(),
                    current.get(p.itemSeq()).wasteCategory(), p.quantity()));
        }
        try {
            incidents.saveAndFlush(incident);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("异常事件号冲突或联单已存在活动异常: " + request.eventNo());
        }
        return toIncidentResponse(incident);
    }

    // ---- 处置方案 ----

    @Transactional
    public PlanResponse createPlan(String manifestNo, String eventNo, CreatePlanRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        Incident incident = loadIncident(manifestNo, eventNo);

        var existingPlan = plans.findByPlanNo(request.planNo());
        if (existingPlan.isPresent()) {
            IncidentPlan plan = existingPlan.get();
            if (planContentMatches(plan, eventNo, request)) {
                return toPlanResponse(plan);
            }
            throw new ConflictException("处置方案号已存在且内容不一致: " + request.planNo());
        }

        if (!incident.isOpen()) {
            throw new BusinessRuleException("异常已处置完成，不能再提出方案");
        }
        if (plans.findByIncidentIdOrderByIdAsc(incident.getId()).stream().anyMatch(IncidentPlan::isPending)) {
            throw new BusinessRuleException("同一异常同时只能有一个确认中的处置方案");
        }

        boolean diversion = incident.getType() == IncidentType.DIVERSION;
        if (diversion) {
            if (request.newDisposerId() == null || request.newDisposerId().isBlank()
                    || request.estimatedArrivalAt() == null) {
                throw new BusinessRuleException("改道方案必须指定新的处置方和预计到达时间");
            }
            if (request.newDisposerId().equals(manifest.getDisposerId())) {
                throw new BusinessRuleException("新处置方不能与原处置方相同");
            }
        } else {
            if (request.newDisposerId() != null || request.estimatedArrivalAt() != null) {
                throw new BusinessRuleException("仅改道方案可指定新处置方和预计到达时间");
            }
        }
        if (manifest.isRegulatoryFrozen()) {
            throw new BusinessRuleException("联单已被监管冻结，不得提出处置方案");
        }
        if (manifest.getCurrentVersionNo() != incident.getBaseVersionNo()) {
            throw new BusinessRuleException("联单版本已变化，处置方案必须读取新版本重新计算");
        }

        IncidentPlan plan = new IncidentPlan(request.planNo(), incident, incident.getBaseVersionNo(),
                diversion ? request.newDisposerId().trim() : null,
                diversion ? request.estimatedArrivalAt() : null,
                request.isQuantityChanged(), request.isCategoryChanged());
        try {
            plans.saveAndFlush(plan);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("方案号冲突或该异常已存在确认中的方案: " + request.planNo());
        }
        incident.addPlan(plan);
        return toPlanResponse(plan);
    }

    /**
     * 方案确认。终止性标记（拒绝/撤回/冻结/版本失效）需要随异常一起落库，
     * 因此业务规则异常不回滚；冲突异常仍整体回滚。
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public PlanResponse decide(String manifestNo, String eventNo, String planNo, PlanDecisionRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        Incident incident = loadIncident(manifestNo, eventNo);
        IncidentPlan plan = loadPlan(incident, planNo);

        var existing = decisions.findByDecisionNo(request.decisionNo());
        if (existing.isPresent()) {
            IncidentPlanDecision decision = existing.get();
            if (decision.matches(planNo, request.role(), request.value(), request.occurredAt())
                    && decision.getPlan().getIncident().getEventNo().equals(eventNo)) {
                return toPlanResponse(decision.getPlan());
            }
            throw new ConflictException("方案决定事件号已存在且内容不一致: " + request.decisionNo());
        }

        if (!plan.isPending()) {
            throw new BusinessRuleException("处置方案已结束（" + plan.getStatus() + "），不能再提交确认");
        }
        if (manifest.isRegulatoryFrozen()) {
            plan.markFrozen();
            throw new BusinessRuleException("联单已被监管冻结，方案 " + planNo + " 终止、不得落地");
        }
        if (manifest.getCurrentVersionNo() != plan.getBaseVersionNo()) {
            plan.markSuperseded();
            throw new BusinessRuleException("联单版本已变化，方案 " + planNo + " 终止、不得落地");
        }
        if (!incident.isOpen()) {
            plan.markSuperseded();
            throw new BusinessRuleException("异常已被其他方案处置，方案 " + planNo + " 终止");
        }

        Set<PlanRole> required = requiredRoles(plan);
        if (!required.contains(request.role())) {
            throw new BusinessRuleException("该责任方与本次处置无关，无需确认: " + request.role());
        }
        for (IncidentPlanDecision d : plan.getDecisions()) {
            if (d.getRole() == request.role()) {
                throw new BusinessRuleException("该责任方已对本方案作出确认: " + request.role());
            }
            if (request.occurredAt().isBefore(d.getOccurredAt())) {
                throw new BusinessRuleException(
                        "确认时间早于已有确认时间，拒绝时间倒序: " + request.occurredAt()
                                + " < " + d.getOccurredAt());
            }
        }
        if (request.value() == DecisionValue.WITHDRAWN && request.role() != PlanRole.TRANSPORTER) {
            throw new BusinessRuleException("仅方案提出方（承运方）可以撤回报批");
        }

        IncidentPlanDecision decision = new IncidentPlanDecision(request.decisionNo(), plan,
                request.role(), request.value(), request.occurredAt());
        try {
            decisions.saveAndFlush(decision);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("决定事件号已存在: " + request.decisionNo());
        }
        plan.addDecision(decision);

        switch (request.value()) {
            case REJECTED -> plan.markRejected();
            case WITHDRAWN -> plan.markWithdrawn();
            case APPROVED -> {
                if (allRequiredApproved(plan, required)) {
                    makeEffective(incident, plan);
                }
            }
        }
        return toPlanResponse(plan);
    }

    // ---- 生效：一次性拆分，不留半条运输链 ----

    private void makeEffective(Incident incident, IncidentPlan plan) {
        Manifest manifest = incident.getManifest();
        if (manifest.isRegulatoryFrozen()) {
            plan.markFrozen();
            throw new BusinessRuleException("联单已被监管冻结，方案不得落地");
        }
        if (manifest.getCurrentVersionNo() != plan.getBaseVersionNo()) {
            plan.markSuperseded();
            throw new BusinessRuleException("联单版本已变化，方案不得落地");
        }
        if (!incident.isOpen()) {
            plan.markSuperseded();
            throw new BusinessRuleException("异常已被处置，方案不得重复落地");
        }

        boolean diversion = incident.getType() == IncidentType.DIVERSION;

        // 先在当前有效版本快照上完成全部校验，任一不过直接终止，不写任何拆分记录，
        // 保证整次处置要么一次性留下完整运输链，要么什么都不留。
        Map<Integer, PackageSnapshot> current = currentPackages(manifest);
        record SplitDraft(PackageSnapshot snapshot, IncidentAffectedPackage affected) {
        }
        List<SplitDraft> drafts = new ArrayList<>();
        for (IncidentAffectedPackage affected : incident.getAffectedPackages()) {
            PackageSnapshot snapshot = current.get(affected.getItemSeq());
            if (snapshot == null || affected.getAffectedQuantity() > snapshot.packageCount()) {
                plan.markSuperseded();
                throw new BusinessRuleException(
                        "包装去向已变化或版本已更新，方案不得落地: 明细 " + affected.getItemSeq());
            }
            drafts.add(new SplitDraft(snapshot, affected));
        }

        TransportSegment segment = null;
        LossRecord loss = null;
        if (diversion) {
            segment = new TransportSegment(plan.getPlanNo() + "-SEG", manifest, incident, plan,
                    plan.getNewDisposerId(), plan.getEstimatedArrivalAt());
            segments.save(segment);
        } else {
            loss = new LossRecord(plan.getPlanNo() + "-LOSS", manifest, incident, plan,
                    incident.getType());
            losses.save(loss);
        }

        for (SplitDraft draft : drafts) {
            PackageSnapshot snapshot = draft.snapshot();
            IncidentAffectedPackage affected = draft.affected();
            int unaffected = snapshot.packageCount() - affected.getAffectedQuantity();
            IncidentPackageSplit row = new IncidentPackageSplit(plan, incident, affected.getItemSeq(),
                    affected.getWasteCategory(), snapshot.packageCount(),
                    affected.getAffectedQuantity(), unaffected,
                    diversion ? SplitTargetType.SEGMENT : SplitTargetType.LOSS,
                    segment, loss);
            splits.save(row);
            if (segment != null) {
                segment.addPackage(row);
            }
        }

        plan.markEffective();
        incident.markResolved();
    }

    // ---- 新运输段交接 ----

    @Transactional
    public TransportSegmentResponse segmentHandover(String manifestNo, String segmentNo,
                                                    SegmentEventRequest request) {
        Manifest manifest = lockManifest(manifestNo);
        TransportSegment segment = segments.findBySegmentNo(segmentNo)
                .orElseThrow(() -> new BusinessRuleException("新运输段不存在: " + segmentNo));
        if (!segment.getManifest().getManifestNo().equals(manifestNo)) {
            throw new BusinessRuleException("新运输段不属于该联单: " + segmentNo);
        }

        var existing = segmentEvents.findByEventNo(request.eventNo());
        if (existing.isPresent()) {
            SegmentEvent event = existing.get();
            if (event.getSegment().getSegmentNo().equals(segmentNo)
                    && event.getRole() == request.role()
                    && event.getWeight().compareTo(normalize(request.weight())) == 0
                    && event.getOccurredAt().equals(request.occurredAt())) {
                return toSegmentResponse(segment);
            }
            throw new ConflictException("运输段事件号已存在且内容不一致: " + request.eventNo());
        }

        CustodianRole expected = switch (segment.getStatus()) {
            case IN_TRANSIT -> segmentEvents.findBySegmentIdOrderByIdAsc(segment.getId()).isEmpty()
                    ? CustodianRole.TRANSPORTER : CustodianRole.DISPOSER;
            case DELIVERED -> throw new BusinessRuleException("新运输段已完成交接");
        };
        if (request.role() != expected) {
            throw new BusinessRuleException(
                    "新运输段交接顺序错误，当前应由 " + expected + " 提交，实际为 " + request.role());
        }
        List<SegmentEvent> chain = segmentEvents.findBySegmentIdOrderByIdAsc(segment.getId());
        if (!chain.isEmpty() && request.occurredAt().isBefore(chain.get(chain.size() - 1).getOccurredAt())) {
            throw new BusinessRuleException("事件时间早于上一事件时间，拒绝时间倒序");
        }

        SegmentEvent event = new SegmentEvent(request.eventNo(), segment, request.role(),
                normalize(request.weight()), request.occurredAt());
        try {
            segmentEvents.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("运输段事件号已存在: " + request.eventNo());
        }
        if (request.role() == CustodianRole.DISPOSER) {
            segment.markDelivered();
        }
        return toSegmentResponse(segment);
    }

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents(String manifestNo) {
        requireManifest(manifestNo);
        return incidents.findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(this::toIncidentResponse).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(String manifestNo, String eventNo) {
        return toIncidentResponse(loadIncident(manifestNo, eventNo));
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(String manifestNo, String eventNo, String planNo) {
        return toPlanResponse(loadPlan(loadIncident(manifestNo, eventNo), planNo));
    }

    @Transactional(readOnly = true)
    public List<TransportSegmentResponse> listSegments(String manifestNo) {
        requireManifest(manifestNo);
        return segments.findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(this::toSegmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LossRecordResponse> listLosses(String manifestNo) {
        requireManifest(manifestNo);
        return losses.findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(this::toLossResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SegmentEventResponse> getSegmentTimeline(String manifestNo, String segmentNo) {
        requireManifest(manifestNo);
        TransportSegment segment = segments.findBySegmentNo(segmentNo)
                .orElseThrow(() -> new BusinessRuleException("新运输段不存在: " + segmentNo));
        if (!segment.getManifest().getManifestNo().equals(manifestNo)) {
            throw new BusinessRuleException("新运输段不属于该联单: " + segmentNo);
        }
        return segmentEvents.findBySegmentIdOrderByIdAsc(segment.getId()).stream()
                .map(this::toSegmentEventResponse).toList();
    }

    @Transactional(readOnly = true)
    public IncidentRecordResponse getRecord(String manifestNo, ManifestRecordResponse baseRecord) {
        requireManifest(manifestNo);
        List<IncidentResponse> incidentResponses = incidents.findByManifestManifestNoOrderByIdAsc(manifestNo)
                .stream().map(this::toIncidentResponse).toList();
        List<TransportSegmentResponse> segmentResponses = segments
                .findByManifestManifestNoOrderByIdAsc(manifestNo).stream()
                .map(this::toSegmentResponse).toList();
        List<LossRecordResponse> lossResponses = losses.findByManifestManifestNoOrderByIdAsc(manifestNo)
                .stream().map(this::toLossResponse).toList();
        return new IncidentRecordResponse(baseRecord, incidentResponses, segmentResponses, lossResponses);
    }

    // ---- 校验辅助 ----

    private record PackageSnapshot(String wasteCategory, int packageCount) {
    }

    /**
     * 当前有效版本的包装快照。已完成联单取当前有效版本；运输途中尚无版本快照，
     * 取联单原始明细（此时版本 1 尚未固化）。
     */
    private Map<Integer, PackageSnapshot> currentPackages(Manifest manifest) {
        ManifestVersion effective = versions.findEffectiveByManifest(manifest).orElse(null);
        Map<Integer, PackageSnapshot> result = new LinkedHashMap<>();
        if (effective != null) {
            for (ManifestVersionItem item : effective.getItems()) {
                result.put(item.getItemSeq(),
                        new PackageSnapshot(item.getWasteCategory(), item.getPackageCount()));
            }
        } else {
            for (ManifestItem item : manifest.getItems()) {
                int seq = result.size() + 1;
                result.put(seq, new PackageSnapshot(item.getWasteCategory(), item.getPackageCount()));
            }
        }
        return result;
    }

    private Set<PlanRole> requiredRoles(IncidentPlan plan) {
        EnumSet<PlanRole> roles = EnumSet.of(
                PlanRole.GENERATOR, PlanRole.TRANSPORTER, PlanRole.OLD_DISPOSER);
        if (plan.getIncident().getType() == IncidentType.DIVERSION) {
            roles.add(PlanRole.NEW_DISPOSER);
        }
        if (plan.isQuantityChanged() || plan.isCategoryChanged()) {
            roles.add(PlanRole.REGULATOR);
        }
        return roles;
    }

    private boolean allRequiredApproved(IncidentPlan plan, Set<PlanRole> required) {
        Set<PlanRole> approved = EnumSet.noneOf(PlanRole.class);
        for (IncidentPlanDecision d : plan.getDecisions()) {
            if (d.getValue() == DecisionValue.APPROVED) {
                approved.add(d.getRole());
            }
        }
        return approved.containsAll(required);
    }

    private boolean incidentContentMatches(Incident incident, String manifestNo,
                                           RegisterIncidentRequest request) {
        if (!incident.getManifest().getManifestNo().equals(manifestNo)
                || incident.getType() != request.type()
                || !incident.getOccurredAt().equals(request.occurredAt())
                || !incident.getLocation().equals(request.location())
                || !incident.getEvidenceRef().equals(request.evidenceRef())
                || incident.getAffectedPackages().size() != request.affectedPackages().size()) {
            return false;
        }
        Map<Integer, Integer> expected = new HashMap<>();
        for (IncidentAffectedPackage p : incident.getAffectedPackages()) {
            expected.put(p.getItemSeq(), p.getAffectedQuantity());
        }
        for (AffectedPackageRequest p : request.affectedPackages()) {
            if (!Integer.valueOf(p.quantity()).equals(expected.remove(p.itemSeq()))) {
                return false;
            }
        }
        return expected.isEmpty();
    }

    private boolean planContentMatches(IncidentPlan plan, String eventNo, CreatePlanRequest request) {
        if (!plan.getIncident().getEventNo().equals(eventNo)
                || plan.isQuantityChanged() != request.isQuantityChanged()
                || plan.isCategoryChanged() != request.isCategoryChanged()) {
            return false;
        }
        String expectedDisposer = plan.getNewDisposerId();
        String actualDisposer = request.newDisposerId() == null ? null : request.newDisposerId().trim();
        if (expectedDisposer == null ? actualDisposer != null : !expectedDisposer.equals(actualDisposer)) {
            return false;
        }
        return plan.getEstimatedArrivalAt() == null
                ? request.estimatedArrivalAt() == null
                : plan.getEstimatedArrivalAt().equals(request.estimatedArrivalAt());
    }

    private Manifest lockManifest(String manifestNo) {
        return manifests.findByManifestNoForUpdate(manifestNo)
                .orElseThrow(() -> new ManifestNotFoundException(manifestNo));
    }

    private void requireManifest(String manifestNo) {
        if (!manifests.existsByManifestNo(manifestNo)) {
            throw new ManifestNotFoundException(manifestNo);
        }
    }

    private Incident loadIncident(String manifestNo, String eventNo) {
        Incident incident = incidents.findByEventNo(eventNo)
                .orElseThrow(() -> new IncidentNotFoundException(eventNo));
        if (!incident.getManifest().getManifestNo().equals(manifestNo)) {
            throw new IncidentNotFoundException(eventNo);
        }
        return incident;
    }

    private IncidentPlan loadPlan(Incident incident, String planNo) {
        IncidentPlan plan = plans.findByPlanNo(planNo)
                .orElseThrow(() -> new PlanNotFoundException(planNo));
        if (!plan.getIncident().getId().equals(incident.getId())) {
            throw new PlanNotFoundException(planNo);
        }
        return plan;
    }

    private static BigDecimal normalize(BigDecimal weight) {
        return weight.setScale(3, RoundingMode.UNNECESSARY);
    }

    // ---- 映射 ----

    private IncidentResponse toIncidentResponse(Incident incident) {
        List<AffectedPackageResponse> affected = incident.getAffectedPackages().stream()
                .map(p -> new AffectedPackageResponse(p.getItemSeq(), p.getWasteCategory(),
                        p.getAffectedQuantity()))
                .toList();
        List<PlanResponse> planResponses = plans.findByIncidentIdOrderByIdAsc(incident.getId()).stream()
                .map(this::toPlanResponse).toList();
        return new IncidentResponse(incident.getEventNo(), incident.getManifest().getManifestNo(),
                incident.getType(), incident.getStatus(), incident.getOccurredAt(),
                incident.getLocation(), incident.getEvidenceRef(), incident.getBaseVersionNo(),
                affected, planResponses, incident.getCreatedAt(), incident.getResolvedAt());
    }

    private PlanResponse toPlanResponse(IncidentPlan plan) {
        List<PlanDecisionResponse> decisionResponses = plan.getDecisions().stream()
                .map(d -> new PlanDecisionResponse(d.getDecisionNo(), d.getRole(), d.getValue(),
                        d.getOccurredAt(), d.getRecordedAt()))
                .toList();
        List<PackageSplitResponse> splitResponses = splits.findByPlanIdOrderByIdAsc(plan.getId()).stream()
                .map(this::toSplitResponse).toList();
        String segmentNo = null;
        String lossNo = null;
        for (IncidentPackageSplit row : splits.findByPlanIdOrderByIdAsc(plan.getId())) {
            if (row.getSegment() != null) {
                segmentNo = row.getSegment().getSegmentNo();
            }
            if (row.getLoss() != null) {
                lossNo = row.getLoss().getLossNo();
            }
        }
        boolean regulatorRequired = plan.isQuantityChanged() || plan.isCategoryChanged();
        return new PlanResponse(plan.getPlanNo(), plan.getIncident().getEventNo(),
                plan.getBaseVersionNo(), plan.getStatus(), plan.getNewDisposerId(),
                plan.getEstimatedArrivalAt(), plan.isQuantityChanged(), plan.isCategoryChanged(),
                regulatorRequired, decisionResponses, splitResponses, segmentNo, lossNo,
                plan.getCreatedAt(), plan.getClosedAt());
    }

    private PackageSplitResponse toSplitResponse(IncidentPackageSplit row) {
        return new PackageSplitResponse(row.getItemSeq(), row.getWasteCategory(),
                row.getOriginalQuantity(), row.getAffectedQuantity(), row.getUnaffectedQuantity(),
                row.getTargetType(),
                row.getSegment() == null ? null : row.getSegment().getSegmentNo(),
                row.getLoss() == null ? null : row.getLoss().getLossNo());
    }

    private TransportSegmentResponse toSegmentResponse(TransportSegment segment) {
        List<PackageSplitResponse> packages = splits.findBySegmentIdOrderByIdAsc(segment.getId()).stream()
                .map(this::toSplitResponse).toList();
        List<SegmentEventResponse> events = segmentEvents.findBySegmentIdOrderByIdAsc(segment.getId())
                .stream().map(this::toSegmentEventResponse).toList();
        return new TransportSegmentResponse(segment.getSegmentNo(),
                segment.getManifest().getManifestNo(), segment.getIncident().getEventNo(),
                segment.getPlan().getPlanNo(), segment.getNewDisposerId(),
                segment.getEstimatedArrivalAt(), segment.getStatus(), packages, events,
                segment.getEffectiveAt(), segment.getDeliveredAt());
    }

    private LossRecordResponse toLossResponse(LossRecord loss) {
        List<PackageSplitResponse> packages = splits.findByLossIdOrderByIdAsc(loss.getId()).stream()
                .map(this::toSplitResponse).toList();
        return new LossRecordResponse(loss.getLossNo(), loss.getManifest().getManifestNo(),
                loss.getIncident().getEventNo(), loss.getPlan().getPlanNo(), loss.getLossType(),
                packages, loss.getRecordedAt());
    }

    private SegmentEventResponse toSegmentEventResponse(SegmentEvent event) {
        return new SegmentEventResponse(event.getEventNo(), event.getRole(), event.getWeight(),
                event.getOccurredAt(), event.getRecordedAt());
    }
}
