package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        String name,

        @Pattern(regexp = "^(0|\\+84)\\d{9}$", message = "Số điện thoại không hợp lệ")
        String phone,

        String avatarUrl
) {
}