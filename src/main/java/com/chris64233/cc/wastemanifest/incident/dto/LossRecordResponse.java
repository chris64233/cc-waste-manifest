package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentType;

import java.time.Instant;
import java.util.List;

public record LossRecordResponse(
        String lossNo,
        String manifestNo,
        String incidentEventNo,
        String planNo,
        IncidentType lossType,
        List<PackageSplitResponse> packages,
        Instant recordedAt) {
}
