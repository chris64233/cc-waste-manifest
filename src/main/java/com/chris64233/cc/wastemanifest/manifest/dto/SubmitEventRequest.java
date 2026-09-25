package com.chris64233.cc.wastemanifest.manifest.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.chris64233.cc.wastemanifest.manifest.domain.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubmitEventRequest(
        @NotBlank(message = "事件号不能为空")
        String eventNo,
        @NotNull(message = "事件类型不能为空")
        EventType eventType,
        @NotBlank(message = "提交角色不能为空")
        String actorParty,
        @NotNull(message = "称重值不能为空")
        @Positive(message = "称重值必须为正")
        BigDecimal weightValue,
        @NotNull(message = "发生时间不能为空")
        Instant occurredAt) {
}
