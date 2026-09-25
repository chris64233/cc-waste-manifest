package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;

public record ItemView(int lineNo, String category, int packageCount, BigDecimal declaredWeight) {
}
