package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.CorrectionField;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CorrectionChangeRequest(
        @Min(0) int itemIndex,
        @NotNull CorrectionField field,
        @NotBlank @Size(max = 64) String newValue) {
}
