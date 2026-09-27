package com.chris64233.cc.wastemanifest.correction.dto;

import java.math.BigDecimal;

public record VersionItemResponse(
        int itemSeq,
        String wasteCategory,
        int packageCount,
        BigDecimal declaredWeight) {
}
