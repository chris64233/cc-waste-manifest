package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.SegmentStatus;

import java.time.Instant;
import java.util.List;

public record TransportSegmentResponse(
        String segmentNo,
        String manifestNo,
        String incidentEventNo,
        String planNo,
        String newDisposerId,
        Instant estimatedArrivalAt,
        SegmentStatus status,
        List<PackageSplitResponse> packages,
        List<SegmentEventResponse> events,
        Instant effectiveAt,
        Instant deliveredAt) {
}
