package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WasteItemRequest(
        @NotBlank(message = "废物类别不能为空")
        String category,
        @Positive(message = "包装数量必须为正")
        @NotNull(message = "包装数量不能为空")
        Integer packageCount,
        @NotNull(message = "申报重量不能为空")
        @Positive(message = "申报重量必须为正")
        BigDecimal declaredWeight) {
}
