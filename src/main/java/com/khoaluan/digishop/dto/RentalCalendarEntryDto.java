package com.khoaluan.digishop.dto;

import java.time.LocalDate;

public record RentalCalendarEntryDto(
        Long productId,
        String productName,
        String productImageUrl,
        Integer productStockQuantity,
        String productBrand,
        Long orderId,
        String orderCode,
        String recipientName,
        String recipientPhone,
        Integer quantity,
        String status,
        String rentalSlot,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate
) {
}