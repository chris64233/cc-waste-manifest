package com.chris64233.cc.wastemanifest.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 处置方案请求。改道必须提供新处置方与预计到达时间；泄漏/遗失两者必须为空。
 * quantityChanged/categoryChanged 由承运方据实申报，涉及变化时方案需监管决定。
 * 布尔字段省略时按 false 处理。
 */
public record CreatePlanRequest(
        @NotBlank @Size(max = 64) String planNo,
        @Size(max = 64) String newDisposerId,
        Instant estimatedArrivalAt,
        Boolean quantityChanged,
        Boolean categoryChanged) {

    public boolean isQuantityChanged() {
        return Boolean.TRUE.equals(quantityChanged);
    }

    public boolean isCategoryChanged() {
        return Boolean.TRUE.equals(categoryChanged);
    }
}
