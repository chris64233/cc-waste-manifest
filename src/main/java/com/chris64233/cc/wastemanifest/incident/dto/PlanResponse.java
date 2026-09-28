package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.PlanStatus;

import java.time.Instant;
import java.util.List;

public record PlanResponse(
        String planNo,
        String incidentEventNo,
        int baseVersionNo,
        PlanStatus status,
        String newDisposerId,
        Instant estimatedArrivalAt,
        boolean quantityChanged,
        boolean categoryChanged,
        boolean regulatorRequired,
        List<PlanDecisionResponse> decisions,
        List<PackageSplitResponse> splits,
        String segmentNo,
        String lossNo,
        Instant createdAt,
        Instant closedAt) {
}
