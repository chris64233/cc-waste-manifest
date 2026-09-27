package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.VersionSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ManifestVersionView(
        int versionNo,
        VersionSource source,
        String correctionNo,
        BigDecimal declaredTotalWeight,
        List<ManifestItemResponse> items,
        List<CorrectionChangeView> diffFromPrevious,
        Instant createdAt) {
}
