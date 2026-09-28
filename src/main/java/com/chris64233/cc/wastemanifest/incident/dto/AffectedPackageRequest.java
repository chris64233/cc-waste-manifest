package com.chris64233.cc.wastemanifest.incident.dto;

import jakarta.validation.constraints.Positive;

public record AffectedPackageRequest(
        @Positive int itemSeq,
        @Positive int quantity) {
}
