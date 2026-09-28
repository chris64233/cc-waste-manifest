package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.CorrectionService;
import com.chris64233.cc.wastemanifest.incident.dto.CreatePlanRequest;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentRecordResponse;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentResponse;
import com.chris64233.cc.wastemanifest.incident.dto.LossRecordResponse;
import com.chris64233.cc.wastemanifest.incident.dto.PlanDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.PlanResponse;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentEventRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentEventResponse;
import com.chris64233.cc.wastemanifest.incident.dto.TransportSegmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manifests/{manifestNo}")
public class IncidentController {

    private final IncidentService incidentService;
    private final CorrectionService correctionService;

    public IncidentController(IncidentService incidentService, CorrectionService correctionService) {
        this.incidentService = incidentService;
        this.correctionService = correctionService;
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> register(@PathVariable String manifestNo,
                                                     @Valid @RequestBody RegisterIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.register(manifestNo, request));
    }

    @GetMapping("/incidents")
    public List<IncidentResponse> incidents(@PathVariable String manifestNo) {
        return incidentService.listIncidents(manifestNo);
    }

    @GetMapping("/incidents/{eventNo}")
    public IncidentResponse incident(@PathVariable String manifestNo, @PathVariable String eventNo) {
        return incidentService.getIncident(manifestNo, eventNo);
    }

    @PostMapping("/incidents/{eventNo}/plans")
    public ResponseEntity<PlanResponse> createPlan(@PathVariable String manifestNo,
                                                   @PathVariable String eventNo,
                                                   @Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.createPlan(manifestNo, eventNo, request));
    }

    @GetMapping("/incidents/{eventNo}/plans/{planNo}")
    public PlanResponse plan(@PathVariable String manifestNo,
                             @PathVariable String eventNo,
                             @PathVariable String planNo) {
        return incidentService.getPlan(manifestNo, eventNo, planNo);
    }

    @PostMapping("/incidents/{eventNo}/plans/{planNo}/decisions")
    public PlanResponse decide(@PathVariable String manifestNo,
                               @PathVariable String eventNo,
                               @PathVariable String planNo,
                               @Valid @RequestBody PlanDecisionRequest request) {
        return incidentService.decide(manifestNo, eventNo, planNo, request);
    }

    @GetMapping("/segments")
    public List<TransportSegmentResponse> segments(@PathVariable String manifestNo) {
        return incidentService.listSegments(manifestNo);
    }

    @GetMapping("/segments/{segmentNo}/timeline")
    public List<SegmentEventResponse> segmentTimeline(@PathVariable String manifestNo,
                                                      @PathVariable String segmentNo) {
        return incidentService.getSegmentTimeline(manifestNo, segmentNo);
    }

    @PostMapping("/segments/{segmentNo}/handover")
    public TransportSegmentResponse segmentHandover(@PathVariable String manifestNo,
                                                    @PathVariable String segmentNo,
                                                    @Valid @RequestBody SegmentEventRequest request) {
        return incidentService.segmentHandover(manifestNo, segmentNo, request);
    }

    @GetMapping("/losses")
    public List<LossRecordResponse> losses(@PathVariable String manifestNo) {
        return incidentService.listLosses(manifestNo);
    }

    /** 异常处置档案：完整联单档案 + 异常证据/各方决定/拆分关系/监管处理/交接链/运输段与损失。 */
    @GetMapping("/incident-record")
    public IncidentRecordResponse incidentRecord(@PathVariable String manifestNo) {
        return incidentService.getRecord(manifestNo, correctionService.getRecord(manifestNo));
    }
}
