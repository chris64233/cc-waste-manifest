package com.chris64233.cc.wastemanifest.manifest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateManifestRequest(
        @NotBlank @Size(max = 64) String manifestNo,
        @NotBlank @Size(max = 64) String generatorId,
        @NotBlank @Size(max = 64) String transporterId,
        @NotBlank @Size(max = 64) String disposerId,
        @NotEmpty List<@Valid ManifestItemRequest> items) {
}
