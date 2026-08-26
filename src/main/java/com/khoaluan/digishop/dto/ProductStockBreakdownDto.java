package com.khoaluan.digishop.dto;

/**
 * Tách rõ tồn kho của 1 sản phẩm thành 3 con số để admin giám sát:
 * - stockQuantity: tổng số máy vật lý đang sở hữu.
 * - reservedForFutureRentals: số máy đang bị các đơn thuê còn hiệu lực giữ chỗ CÙNG LÚC,
 *   tính đỉnh (peak) từ hôm nay trở đi — xem OrderService#maxConcurrentFutureRentalQuantity.
 * - availableToSell: số máy còn có thể bán đứt ngay bây giờ (= stockQuantity - reservedForFutureRentals, tối thiểu 0).
 */
public record ProductStockBreakdownDto(
        Long productId,
        String productName,
        Integer stockQuantity,
        Integer reservedForFutureRentals,
        Integer availableToSell
) {
}