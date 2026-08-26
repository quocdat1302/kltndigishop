package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.OrderType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CartItemDto(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        BigDecimal unitPrice,
        Integer stockQuantity,
        Boolean productAvailable,
        OrderType orderType,
        Integer quantity,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        Integer rentalDays,
        BigDecimal subtotal
) {
}
