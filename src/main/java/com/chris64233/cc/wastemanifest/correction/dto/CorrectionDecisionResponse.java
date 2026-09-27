package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.DecisionRole;
import com.chris64233.cc.wastemanifest.correction.DecisionValue;

import java.time.Instant;

public record CorrectionDecisionResponse(
        String decisionNo,
        DecisionRole role,
        DecisionValue value,
        Instant occurredAt,
        Instant recordedAt) {
}
