package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentStatus;
import com.chris64233.cc.wastemanifest.incident.IncidentType;

import java.time.Instant;
import java.util.List;

public record IncidentResponse(
        String incidentNo,
        String manifestNo,
        IncidentType type,
        IncidentStatus status,
        int baseVersionNo,
        Instant occurredAt,
        String location,
        String evidenceRef,
        String newDisposerId,
        Instant estimatedArrivalAt,
        boolean regulatorRequired,
        List<AffectedLineResponse> affectedLines,
        List<IncidentDecisionResponse> decisions,
        List<SegmentResponse> segments,
        Instant createdAt,
        Instant closedAt) {
}
