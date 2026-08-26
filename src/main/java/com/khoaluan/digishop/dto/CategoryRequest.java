package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        String name,

        @NotBlank(message = "Loại danh mục không được để trống")
        @Pattern(regexp = "brand|category", message = "type phải là 'brand' hoặc 'category'")
        String type,

        String imageUrl
) {
}