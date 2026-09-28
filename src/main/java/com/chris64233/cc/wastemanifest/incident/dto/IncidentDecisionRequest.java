package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentDecisionValue;
import com.chris64233.cc.wastemanifest.incident.IncidentRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IncidentDecisionRequest(
        @NotBlank @Size(max = 64) String decisionNo,
        @NotNull IncidentRole role,
        @NotNull IncidentDecisionValue value,
        @NotNull Instant occurredAt) {
}
