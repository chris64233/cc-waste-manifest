package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.CorrectionField;

public record FieldChangeResponse(
        CorrectionField field,
        int itemSeq,
        String oldValue,
        String newValue) {
}
