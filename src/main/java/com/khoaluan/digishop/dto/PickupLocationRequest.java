package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PickupLocationRequest(
        @NotBlank(message = "Tên địa điểm không được để trống")
        String name,

        String address,

        @NotNull(message = "Vui lòng nhập phí (0 nếu miễn phí)")
        BigDecimal fee,

        boolean isDelivery,
        boolean active,
        Integer displayOrder
) {
}