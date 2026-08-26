package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

public record TopRentedProductDto(
        Long productId,
        String productName,
        String brand,
        long rentalCount,
        long totalQuantity,
        BigDecimal revenue
) {
}