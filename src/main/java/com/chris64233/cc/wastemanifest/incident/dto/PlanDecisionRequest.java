package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.correction.DecisionValue;
import com.chris64233.cc.wastemanifest.incident.PlanRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PlanDecisionRequest(
        @NotBlank @Size(max = 64) String decisionNo,
        @NotNull PlanRole role,
        @NotNull DecisionValue value,
        @NotNull Instant occurredAt) {
}
