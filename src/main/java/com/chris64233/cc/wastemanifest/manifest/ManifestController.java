package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.DisputeConfirmRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.HandoverRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestEventResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestRevisionsResponse;
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
@RequestMapping("/api/manifests")
public class ManifestController {

    private final ManifestService service;
    private final CorrectionService correctionService;

    public ManifestController(ManifestService service, CorrectionService correctionService) {
        this.service = service;
        this.correctionService = correctionService;
    }

    @PostMapping
    public ResponseEntity<ManifestDetailResponse> create(@Valid @RequestBody CreateManifestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{manifestNo}")
    public ManifestDetailResponse detail(@PathVariable String manifestNo) {
        return service.getDetail(manifestNo);
    }

    @GetMapping("/{manifestNo}/timeline")
    public List<ManifestEventResponse> timeline(@PathVariable String manifestNo) {
        return service.getTimeline(manifestNo);
    }

    @GetMapping("/{manifestNo}/revisions")
    public ManifestRevisionsResponse revisions(@PathVariable String manifestNo) {
        return correctionService.getRevisions(manifestNo);
    }

    @PostMapping("/{manifestNo}/handover")
    public ManifestDetailResponse handover(@PathVariable String manifestNo,
                                           @Valid @RequestBody HandoverRequest request) {
        return service.handover(manifestNo, request);
    }

    @PostMapping("/{manifestNo}/dispute-confirmations")
    public ManifestDetailResponse confirmDispute(@PathVariable String manifestNo,
                                                 @Valid @RequestBody DisputeConfirmRequest request) {
        return service.confirmDispute(manifestNo, request);
    }

    @PostMapping("/{manifestNo}/freeze")
    public ManifestDetailResponse freeze(@PathVariable String manifestNo) {
        return service.freeze(manifestNo);
    }

    @PostMapping("/{manifestNo}/unfreeze")
    public ManifestDetailResponse unfreeze(@PathVariable String manifestNo) {
        return service.unfreeze(manifestNo);
    }
}
