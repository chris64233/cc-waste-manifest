package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.incident.dto.IncidentDecisionRequest;
import com.chris64233.cc.wastemanifest.incident.dto.IncidentResponse;
import com.chris64233.cc.wastemanifest.incident.dto.RegisterIncidentRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentHandoverRequest;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentResponse;
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

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> register(@PathVariable String manifestNo,
                                                     @Valid @RequestBody RegisterIncidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(manifestNo, request));
    }

    @GetMapping("/incidents")
    public List<IncidentResponse> list(@PathVariable String manifestNo) {
        return service.listIncidents(manifestNo);
    }

    @GetMapping("/incidents/{incidentNo}")
    public IncidentResponse incident(@PathVariable String manifestNo,
                                     @PathVariable String incidentNo) {
        return service.getIncident(manifestNo, incidentNo);
    }

    @PostMapping("/incidents/{incidentNo}/decisions")
    public IncidentResponse decide(@PathVariable String manifestNo,
                                   @PathVariable String incidentNo,
                                   @Valid @RequestBody IncidentDecisionRequest request) {
        return service.decide(manifestNo, incidentNo, request);
    }

    @GetMapping("/segments")
    public List<SegmentResponse> segments(@PathVariable String manifestNo) {
        return service.listSegments(manifestNo);
    }

    @GetMapping("/segments/{segmentNo}")
    public SegmentResponse segment(@PathVariable String manifestNo,
                                   @PathVariable String segmentNo) {
        return service.getSegment(manifestNo, segmentNo);
    }

    @PostMapping("/segments/{segmentNo}/handover")
    public SegmentResponse segmentHandover(@PathVariable String manifestNo,
                                           @PathVariable String segmentNo,
                                           @Valid @RequestBody SegmentHandoverRequest request) {
        return service.segmentHandover(manifestNo, segmentNo, request);
    }
}
