package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSamplePhotoRequest(
        @NotBlank(message = "Link ảnh không được để trống") String imageUrl,
        String caption
) {
}