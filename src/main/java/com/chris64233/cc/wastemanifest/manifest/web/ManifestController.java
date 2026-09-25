package com.chris64233.cc.wastemanifest.manifest.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.EventSubmissionView;
import com.chris64233.cc.wastemanifest.manifest.dto.EventView;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailView;
import com.chris64233.cc.wastemanifest.manifest.dto.SubmitEventRequest;
import com.chris64233.cc.wastemanifest.manifest.service.ManifestService;

@RestController
@RequestMapping("/api/manifests")
public class ManifestController {

    private final ManifestService manifestService;

    public ManifestController(ManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManifestDetailView create(@Valid @RequestBody CreateManifestRequest request) {
        return manifestService.createManifest(request);
    }

    @GetMapping("/{manifestNo}")
    public ManifestDetailView detail(@PathVariable String manifestNo) {
        return manifestService.getManifest(manifestNo);
    }

    @GetMapping("/{manifestNo}/timeline")
    public List<EventView> timeline(@PathVariable String manifestNo) {
        return manifestService.getTimeline(manifestNo);
    }

    @PostMapping("/{manifestNo}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSubmissionView submitEvent(@PathVariable String manifestNo,
                                           @Valid @RequestBody SubmitEventRequest request) {
        return manifestService.submitEvent(manifestNo, request);
    }
}
