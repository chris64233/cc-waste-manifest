package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.incident.dto.IncidentResponse;
import com.chris64233.cc.wastemanifest.incident.dto.SegmentResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestEventResponse;

import java.util.List;

/**
 * 联单完整档案：当前有效版本、全部版本、版本差异、更正审批时间线、
 * 运输异常处置（含证据、各方决定、包装拆分关系、监管处理）、运输段（含交接链）
 * 与原联单交接事件时间线。
 */
public record ManifestRecordResponse(
        ManifestDetailResponse manifest,
        VersionResponse currentVersion,
        List<VersionResponse> versions,
        List<VersionDiffResponse> versionDiffs,
        List<CorrectionResponse> corrections,
        List<IncidentResponse> incidents,
        List<SegmentResponse> segments,
        List<ManifestEventResponse> timeline) {
}
