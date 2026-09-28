package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record RegisterIncidentRequest(
        @NotBlank @Size(max = 64) String eventNo,
        @NotNull IncidentType type,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 200) String location,
        @NotBlank @Size(max = 500) String evidenceRef,
        @NotEmpty List<@Valid AffectedPackageRequest> affectedPackages) {
}
