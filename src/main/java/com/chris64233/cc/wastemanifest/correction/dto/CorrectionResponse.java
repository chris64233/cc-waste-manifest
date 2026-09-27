package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.CorrectionStatus;
import com.chris64233.cc.wastemanifest.correction.DecisionRole;
import com.chris64233.cc.wastemanifest.correction.DisputeImpact;

import java.time.Instant;
import java.util.List;

public record CorrectionResponse(
        String correctionNo,
        String manifestNo,
        int baseVersionNo,
        Integer resultVersionNo,
        CorrectionStatus status,
        DecisionRole applicantRole,
        String reason,
        String evidenceRef,
        DisputeImpact disputeImpact,
        List<FieldChangeResponse> changes,
        List<CorrectionDecisionResponse> decisions,
        Instant createdAt,
        Instant closedAt) {
}
