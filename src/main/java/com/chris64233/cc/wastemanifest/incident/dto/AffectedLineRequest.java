package com.chris64233.cc.wastemanifest.incident.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AffectedLineRequest(
        @NotNull Integer itemSeq,
        @Positive int packageCount,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal weight) {
}
