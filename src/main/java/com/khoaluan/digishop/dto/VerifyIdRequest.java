package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyIdRequest(
        @NotBlank(message = "Vui lòng nhập số CCCD/CMND")
        @Pattern(regexp = "^\\d{9}$|^\\d{12}$", message = "Số CCCD/CMND phải gồm 9 hoặc 12 chữ số")
        String idCardNumber,

        @NotBlank(message = "Vui lòng cung cấp ảnh mặt trước CCCD/CMND")
        String idCardFrontUrl,

        @NotBlank(message = "Vui lòng cung cấp ảnh mặt sau CCCD/CMND")
        String idCardBackUrl
) {
}