package com.khoaluan.digishop.dto;

import java.util.List;

/**
 * Tồn kho thuê của 1 sản phẩm tại 1 ngày cụ thể — dùng cho trang admin "Kiểm soát tồn kho thuê".
 */
public record RentalInventoryEntryDto(
        Long productId,
        String productName,
        String productImageUrl,
        Integer stockQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        List<String> bookedSlots
) {
}