package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailResponse;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestEventResponse;

import java.util.List;

/**
 * 联单完整档案：当前有效版本、全部版本、版本差异、更正审批时间线与交接事件时间线。
 */
public record ManifestRecordResponse(
        ManifestDetailResponse manifest,
        VersionResponse currentVersion,
        List<VersionResponse> versions,
        List<VersionDiffResponse> versionDiffs,
        List<CorrectionResponse> corrections,
        List<ManifestEventResponse> timeline) {
}
