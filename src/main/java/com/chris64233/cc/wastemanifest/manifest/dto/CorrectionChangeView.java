package com.chris64233.cc.wastemanifest.manifest.dto;

import com.chris64233.cc.wastemanifest.manifest.CorrectionField;

public record CorrectionChangeView(int itemIndex, CorrectionField field, String oldValue, String newValue) {
}
