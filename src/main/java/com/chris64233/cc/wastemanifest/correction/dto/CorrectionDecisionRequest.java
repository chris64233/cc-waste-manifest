package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.DecisionRole;
import com.chris64233.cc.wastemanifest.correction.DecisionValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CorrectionDecisionRequest(
        @NotBlank @Size(max = 64) String decisionNo,
        @NotNull DecisionRole role,
        @NotNull DecisionValue value,
        @NotNull Instant occurredAt) {
}
