package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.ApproverRole;
import com.chris64233.cc.wastemanifest.manifest.CorrectionStatus;
import com.chris64233.cc.wastemanifest.manifest.DisputeImpact;

import java.time.Instant;
import java.util.List;

public record CorrectionResponse(
        String correctionNo,
        String manifestNo,
        CorrectionStatus status,
        int baseVersionNo,
        Integer resultVersionNo,
        String reason,
        String evidence,
        DisputeImpact disputeImpact,
        List<ApproverRole> requiredApprovals,
        List<CorrectionChangeView> changes,
        List<CorrectionDecisionView> decisions,
        Instant createdAt,
        Instant closedAt) {
}
