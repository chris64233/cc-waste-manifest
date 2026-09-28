package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentStatus;
import com.chris64233.cc.wastemanifest.incident.IncidentType;

import java.math.BigDecimal;
import java.time.Instant;

public record AffectedLineResponse(
        int itemSeq,
        String wasteCategory,
        int affectedPackageCount,
        BigDecimal affectedWeight,
        Integer resultPackageCount,
        BigDecimal resultWeight,
        String resultWasteCategory) {
}
