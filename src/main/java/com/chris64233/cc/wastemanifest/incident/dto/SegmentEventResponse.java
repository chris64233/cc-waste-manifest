package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;

import java.math.BigDecimal;
import java.time.Instant;

public record SegmentEventResponse(
        String eventNo,
        CustodianRole role,
        BigDecimal weight,
        Instant occurredAt,
        Instant recordedAt) {
}
