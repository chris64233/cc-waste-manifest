package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.SegmentEventType;

import java.math.BigDecimal;
import java.time.Instant;

public record SegmentHandoverEventResponse(
        String eventNo,
        SegmentEventType type,
        BigDecimal weight,
        Instant occurredAt,
        Instant recordedAt) {
}
