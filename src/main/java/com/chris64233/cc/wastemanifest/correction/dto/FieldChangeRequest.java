package com.chris64233.cc.wastemanifest.correction.dto;

import com.chris64233.cc.wastemanifest.correction.CorrectionField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 单项更正：字段、明细序号（从 1 开始）与新值。原始值由服务端从基线版本快照取得，
 * 不采信调用方传入的旧值。
 */
public record FieldChangeRequest(
        @NotNull CorrectionField field,
        @Positive int itemSeq,
        @NotBlank @Size(max = 64) String newValue) {
}
