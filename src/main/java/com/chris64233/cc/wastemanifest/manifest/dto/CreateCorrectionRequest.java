package com.chris64233.cc.wastemanifest.manifest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCorrectionRequest(
        @NotBlank @Size(max = 64) String correctionNo,
        @NotBlank @Size(max = 512) String reason,
        @NotBlank @Size(max = 512) String evidence,
        @NotEmpty List<@Valid CorrectionChangeRequest> changes) {
}
