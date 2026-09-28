package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.correction.DecisionValue;
import com.chris64233.cc.wastemanifest.incident.PlanRole;

import java.time.Instant;

public record PlanDecisionResponse(
        String decisionNo,
        PlanRole role,
        DecisionValue value,
        Instant occurredAt,
        Instant recordedAt) {
}
