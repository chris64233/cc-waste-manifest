package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import com.chris64233.cc.wastemanifest.manifest.ManifestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ManifestDetailResponse(
        String manifestNo,
        String generatorId,
        String transporterId,
        String disposerId,
        ManifestStatus status,
        CustodianRole currentCustodian,
        BigDecimal declaredTotalWeight,
        BigDecimal receivedWeight,
        BigDecimal finalWeight,
        List<ManifestItemResponse> items,
        Instant createdAt,
        int currentVersionNo,
        boolean regulatoryFrozen) {
}
