package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.correction.dto.ManifestRecordResponse;

import java.util.List;

/**
 * 联单异常档案：在完整联单档案之上追加异常、包装拆分、新运输段、损失记录。
 */
public record IncidentRecordResponse(
        ManifestRecordResponse record,
        List<IncidentResponse> incidents,
        List<TransportSegmentResponse> segments,
        List<LossRecordResponse> losses) {
}
