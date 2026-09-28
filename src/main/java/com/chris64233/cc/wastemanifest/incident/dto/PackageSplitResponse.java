package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.SplitTargetType;

public record PackageSplitResponse(
        int itemSeq,
        String wasteCategory,
        int originalQuantity,
        int affectedQuantity,
        int unaffectedQuantity,
        SplitTargetType targetType,
        String segmentNo,
        String lossNo) {
}
