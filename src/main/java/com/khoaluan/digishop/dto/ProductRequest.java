package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Dùng cho cả tạo mới lẫn cập nhật sản phẩm (Admin).
 * Khi cập nhật, field null nghĩa là "giữ nguyên giá trị cũ" — service sẽ tự bỏ qua.
 */
public record ProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        String name,

        @NotBlank(message = "Hãng không được để trống")
        String brand,

        @NotBlank(message = "Loại sản phẩm không được để trống")
        String type,

        @NotNull(message = "Giá bán không được để trống")
        @DecimalMin(value = "0", inclusive = true, message = "Giá bán phải >= 0")
        BigDecimal buyPrice,

        @NotNull(message = "Giá thuê theo ngày không được để trống")
        @DecimalMin(value = "0", inclusive = true, message = "Giá thuê phải >= 0")
        BigDecimal rentPrice,

        @DecimalMin(value = "0", inclusive = true, message = "Giá thuê theo tuần phải >= 0")
        BigDecimal rentPriceWeekly,

        @DecimalMin(value = "0", inclusive = true, message = "Giá thuê khung sáng phải >= 0")
        BigDecimal rentPriceMorning,

        @DecimalMin(value = "0", inclusive = true, message = "Giá thuê khung chiều phải >= 0")
        BigDecimal rentPriceAfternoon,

        @DecimalMin(value = "0", inclusive = true, message = "Giá thuê khung tối phải >= 0")
        BigDecimal rentPriceEvening,

        String accessoriesIncluded,

        String techSpecs,

        String lensMount,

        String imageUrl,

        String description,

        @NotNull(message = "Số lượng tồn không được để trống")
        @Min(value = 0, message = "Số lượng tồn phải >= 0")
        Integer stockQuantity,

        Boolean isAvailable,

        @NotBlank(message = "Tình trạng sản phẩm không được để trống")
        String productCondition,

        Boolean isNew,

        Boolean isHot
) {
}