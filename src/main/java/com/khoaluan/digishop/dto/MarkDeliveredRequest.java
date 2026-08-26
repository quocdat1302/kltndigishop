package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkDeliveredRequest(
        @NotBlank(message = "Vui lòng ghi nhận tình trạng máy lúc giao")
        String conditionNote
) {
}