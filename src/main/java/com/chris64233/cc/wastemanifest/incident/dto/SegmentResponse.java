package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.SegmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SegmentResponse(
        String segmentNo,
        String incidentNo,
        String parentManifestNo,
        int seqNo,
        String type,
        String destinationDisposerId,
        Instant estimatedArrivalAt,
        SegmentStatus status,
        String currentCustodian,
        BigDecimal declaredWeight,
        BigDecimal receivedWeight,
        List<SegmentItemResponse> items,
        List<SegmentHandoverEventResponse> handoverEvents,
        Instant effectiveAt,
        Instant completedAt) {
}
