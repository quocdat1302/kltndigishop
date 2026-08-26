package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        Integer rentalDays,
        String rentalSlot,
        BigDecimal subtotal
) {
}