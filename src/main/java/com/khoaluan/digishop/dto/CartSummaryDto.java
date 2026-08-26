package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartSummaryDto(
        List<CartItemDto> purchaseItems,
        List<CartItemDto> rentalItems,
        int totalItems,
        BigDecimal purchaseSubtotal,
        BigDecimal rentalSubtotal
) {
}
