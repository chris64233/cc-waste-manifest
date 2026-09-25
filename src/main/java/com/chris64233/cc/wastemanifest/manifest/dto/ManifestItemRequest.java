package com.chris64233.cc.wastemanifest.manifest.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ManifestItemRequest(
        @NotBlank @Size(max = 64) String wasteCategory,
        @Positive int packageCount,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal declaredWeight) {
}
