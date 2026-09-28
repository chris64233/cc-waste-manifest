package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentStatus;
import com.chris64233.cc.wastemanifest.incident.IncidentType;

import java.time.Instant;
import java.util.List;

public record IncidentResponse(
        String eventNo,
        String manifestNo,
        IncidentType type,
        IncidentStatus status,
        Instant occurredAt,
        String location,
        String evidenceRef,
        int baseVersionNo,
        List<AffectedPackageResponse> affectedPackages,
        List<PlanResponse> plans,
        Instant createdAt,
        Instant resolvedAt) {
}
