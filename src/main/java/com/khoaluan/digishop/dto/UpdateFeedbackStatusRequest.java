package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFeedbackStatusRequest(
        @NotBlank(message = "Trạng thái không được để trống")
        String status
) {
}