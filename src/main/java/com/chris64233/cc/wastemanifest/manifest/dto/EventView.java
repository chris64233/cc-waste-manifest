package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EventView(
        int sequence,
        String eventNo,
        String eventType,
        String actorParty,
        BigDecimal weightValue,
        Instant occurredAt,
        Instant recordedAt) {
}
