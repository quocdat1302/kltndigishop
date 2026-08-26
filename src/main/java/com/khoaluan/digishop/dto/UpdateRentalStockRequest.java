package com.khoaluan.digishop.dto;

import lombok.Data;

@Data
public class UpdateRentalStockRequest {
    /** Số lượng máy cho thuê mới. */
    private Integer rentalStockQuantity;

    /** Ghi chú (tuỳ chọn). */
    private String note;
}
