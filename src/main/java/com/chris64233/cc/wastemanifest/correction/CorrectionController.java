package com.chris64233.cc.wastemanifest.correction;

import com.chris64233.cc.wastemanifest.correction.dto.CorrectionDecisionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.CorrectionResponse;
import com.chris64233.cc.wastemanifest.correction.dto.CreateCorrectionRequest;
import com.chris64233.cc.wastemanifest.correction.dto.ManifestRecordResponse;
import com.chris64233.cc.wastemanifest.correction.dto.VersionDiffResponse;
import com.chris64233.cc.wastemanifest.correction.dto.VersionResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manifests/{manifestNo}")
public class CorrectionController {

    private final CorrectionService service;

    public CorrectionController(CorrectionService service) {
        this.service = service;
    }

    @PostMapping("/corrections")
    public ResponseEntity<CorrectionResponse> create(@PathVariable String manifestNo,
                                                     @Valid @RequestBody CreateCorrectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createCorrection(manifestNo, request));
    }

    @GetMapping("/corrections/{correctionNo}")
    public CorrectionResponse correction(@PathVariable String manifestNo,
                                         @PathVariable String correctionNo) {
        return service.getCorrection(manifestNo, correctionNo);
    }

    @PostMapping("/corrections/{correctionNo}/decisions")
    public CorrectionResponse decide(@PathVariable String manifestNo,
                                     @PathVariable String correctionNo,
                                     @Valid @RequestBody CorrectionDecisionRequest request) {
        return service.decide(manifestNo, correctionNo, request);
    }

    @PostMapping("/freeze")
    public ManifestDetailResponse freeze(@PathVariable String manifestNo) {
        return service.freeze(manifestNo);
    }

    @GetMapping("/versions")
    public List<VersionResponse> versions(@PathVariable String manifestNo) {
        return service.getVersions(manifestNo);
    }

    @GetMapping("/versions/diff")
    public VersionDiffResponse diff(@PathVariable String manifestNo,
                                    @RequestParam int from, @RequestParam int to) {
        return service.getDiff(manifestNo, from, to);
    }

    @GetMapping("/record")
    public ManifestRecordResponse record(@PathVariable String manifestNo) {
        return service.getRecord(manifestNo);
    }
}
