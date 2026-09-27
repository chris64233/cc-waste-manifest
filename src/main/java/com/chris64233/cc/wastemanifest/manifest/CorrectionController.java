package com.chris64233.cc.wastemanifest.manifest;

import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.CorrectionResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateCorrectionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manifests/{manifestNo}/corrections")
public class CorrectionController {

    private final CorrectionService service;

    public CorrectionController(CorrectionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CorrectionResponse> create(@PathVariable String manifestNo,
                                                     @Valid @RequestBody CreateCorrectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCorrection(manifestNo, request));
    }

    @GetMapping("/{correctionNo}")
    public CorrectionResponse detail(@PathVariable String manifestNo, @PathVariable String correctionNo) {
        return service.getCorrection(manifestNo, correctionNo);
    }

    @PostMapping("/{correctionNo}/decisions")
    public CorrectionResponse decide(@PathVariable String manifestNo, @PathVariable String correctionNo,
                                     @Valid @RequestBody CorrectionDecisionRequest request) {
        return service.decide(manifestNo, correctionNo, request);
    }
}
