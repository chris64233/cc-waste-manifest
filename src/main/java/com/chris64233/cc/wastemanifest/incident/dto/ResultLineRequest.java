package com.chris64233.cc.wastemanifest.incident.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 处置口径行：受影响包装经处置（清理、回收、重新包装、类别鉴别）后的结果。
 * 任一字段为空表示该维度沿用受影响口径；遗失时结果数量/重量由服务端置 0。
 */
public record ResultLineRequest(
        @PositiveOrZero Integer packageCount,
        @PositiveOrZero @Digits(integer = 16, fraction = 3) BigDecimal weight,
        @Size(max = 64) String wasteCategory) {
}
