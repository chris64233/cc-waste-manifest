package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ManifestDetailView(
        Long id,
        String manifestNo,
        String generatorParty,
        String carrierParty,
        String disposerParty,
        String status,
        String currentCustodian,
        List<ItemView> items,
        BigDecimal declaredTotalWeight,
        BigDecimal weightToleranceRatio,
        BigDecimal generatorWeight,
        BigDecimal carrierWeight,
        BigDecimal disposerWeight,
        BigDecimal resolvedWeight,
        Instant lastEventAt,
        Instant createdAt) {
}
