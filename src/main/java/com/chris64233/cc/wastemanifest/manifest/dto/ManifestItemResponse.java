package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;

public record ManifestItemResponse(String wasteCategory, int packageCount, BigDecimal declaredWeight) {
}
