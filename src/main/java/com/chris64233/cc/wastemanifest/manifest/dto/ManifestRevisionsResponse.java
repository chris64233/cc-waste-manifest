package com.chris64233.cc.wastemanifest.manifest.dto;

import java.util.List;

public record ManifestRevisionsResponse(
        String manifestNo,
        int currentVersionNo,
        List<ManifestVersionView> versions,
        List<CorrectionResponse> corrections) {
}
