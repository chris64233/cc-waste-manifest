package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record HandoverRequest(
        @NotBlank @Size(max = 64) String eventNo,
        @NotNull CustodianRole role,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal weight,
        @NotNull Instant occurredAt) {
}
