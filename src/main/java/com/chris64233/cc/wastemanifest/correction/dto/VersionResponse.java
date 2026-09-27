package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.VersionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VersionResponse(
        int versionNo,
        VersionStatus status,
        BigDecimal declaredTotalWeight,
        BigDecimal receivedWeight,
        BigDecimal finalWeight,
        List<VersionItemResponse> items,
        Instant effectiveAt) {
}
