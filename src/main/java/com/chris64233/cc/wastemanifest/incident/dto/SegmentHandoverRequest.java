package com.chris64233.cc.wastemanifest.incident.dto;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 新运输段交接事件。角色由段状态推导（运输中→承运方交付，已交付→处置方接收），
 * 请求仍需显式给出角色并与预期一致。
 */
public record SegmentHandoverRequest(
        @NotBlank @Size(max = 64) String eventNo,
        @NotNull CustodianRole role,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal weight,
        @NotNull Instant occurredAt) {
}
