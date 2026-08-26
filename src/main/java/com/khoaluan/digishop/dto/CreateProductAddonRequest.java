package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateProductAddonRequest(
        @NotBlank(message = "Tên phụ kiện không được để trống") String name,
        BigDecimal price,
        boolean included,
        Integer displayOrder
) {
}