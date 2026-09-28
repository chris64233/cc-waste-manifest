package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentDecisionValue;
import com.chris64233.cc.wastemanifest.incident.IncidentRole;

import java.time.Instant;

public record IncidentDecisionResponse(
        String decisionNo,
        IncidentRole role,
        IncidentDecisionValue value,
        Instant occurredAt,
        Instant recordedAt) {
}
