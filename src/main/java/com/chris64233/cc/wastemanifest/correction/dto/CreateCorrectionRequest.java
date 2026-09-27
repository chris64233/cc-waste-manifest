package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.DecisionRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCorrectionRequest(
        @NotBlank @Size(max = 64) String correctionNo,
        @NotNull DecisionRole applicantRole,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 500) String evidenceRef,
        @NotEmpty List<@Valid FieldChangeRequest> changes) {
}
