package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.EventType;

import java.math.BigDecimal;
import java.time.Instant;

public record ManifestEventResponse(
        String eventNo,
        EventType type,
        CustodianRole role,
        BigDecimal weight,
        Instant occurredAt,
        Instant recordedAt) {
}
