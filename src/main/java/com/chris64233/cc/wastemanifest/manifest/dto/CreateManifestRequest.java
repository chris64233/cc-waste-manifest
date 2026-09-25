package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record CreateManifestRequest(
        @NotBlank(message = "联单号不能为空")
        String manifestNo,
        @NotBlank(message = "产生方不能为空")
        String generatorParty,
        @NotBlank(message = "指定承运方不能为空")
        String carrierParty,
        @NotBlank(message = "指定处置方不能为空")
        String disposerParty,
        @NotEmpty(message = "至少需要一条废物明细")
        List<@jakarta.validation.Valid WasteItemRequest> items,
        BigDecimal weightToleranceRatio) {
}
