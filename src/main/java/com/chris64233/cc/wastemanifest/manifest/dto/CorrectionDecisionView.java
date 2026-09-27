package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.ApproverRole;
import com.chris64233.cc.wastemanifest.manifest.DecisionType;

import java.time.Instant;

public record CorrectionDecisionView(
        String decisionNo,
        ApproverRole role,
        DecisionType decision,
        Instant occurredAt,
        Instant recordedAt) {
}
