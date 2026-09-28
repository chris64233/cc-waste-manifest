package com.chris64233.cc.wastemanifest.incident.dto;

import java.time.Instant;

public record AffectedPackageResponse(
        int itemSeq,
        String wasteCategory,
        int quantity) {
}
