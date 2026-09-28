package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.incident.IncidentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 运输异常登记请求。承运方对运输中的当前有效联单登记泄漏、遗失或改道。
 *
 * @param affectedLines 受影响包装及数量（按联单明细序号定位）
 * @param resultLines   处置口径（可选，顺序与 affectedLines 一一对应）；
 *                      用于表达泄漏清理后重新包装、废物类别或数量变化；
 *                      不传表示处置口径与受影响口径一致（遗失恒为损失 0）
 */
public record RegisterIncidentRequest(
        @NotBlank @Size(max = 64) String incidentNo,
        @NotNull IncidentType type,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 256) String location,
        @NotBlank @Size(max = 500) String evidenceRef,
        @NotEmpty List<@Valid AffectedLineRequest> affectedLines,
        List<@Valid ResultLineRequest> resultLines,
        @Size(max = 64) String newDisposerId,
        Instant estimatedArrivalAt) {
}
