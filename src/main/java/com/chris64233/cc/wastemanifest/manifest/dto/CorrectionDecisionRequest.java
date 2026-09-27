package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.ApproverRole;
import com.chris64233.cc.wastemanifest.manifest.DecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CorrectionDecisionRequest(
        @NotBlank @Size(max = 64) String decisionNo,
        @NotNull ApproverRole role,
        @NotNull DecisionType decision,
        @NotNull Instant occurredAt) {
}
