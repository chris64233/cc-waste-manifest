package com.chris64233.cc.wastemanifest.correction.dto;

import java.util.List;

public record VersionDiffResponse(
        int fromVersionNo,
        int toVersionNo,
        List<FieldChangeResponse> changes) {
}
