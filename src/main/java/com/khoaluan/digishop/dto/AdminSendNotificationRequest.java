package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminSendNotificationRequest(
        @NotNull(message = "Vui lòng chọn khách hàng")
        Long targetUserId,

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        String title,

        @NotBlank(message = "Vui lòng nhập nội dung")
        String message
) {
}