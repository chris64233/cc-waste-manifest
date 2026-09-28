package com.chris64233.cc.wastemanifest.incident.dto;

import java.math.BigDecimal;

public record SegmentItemResponse(
        int sourceItemSeq,
        String wasteCategory,
        int packageCount,
        BigDecimal declaredWeight) {
}
