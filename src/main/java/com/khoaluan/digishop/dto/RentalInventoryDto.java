package com.khoaluan.digishop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalInventoryDto {
    /** ID sản phẩm. */
    private Long id;

    /** Tên sản phẩm. */
    private String name;

    /** Brand. */
    private String brand;

    /** Loại (DSLR, Mirrorless, Lens, ...). */
    private String type;

    /** Giá thuê theo ngày. */
    private BigDecimal rentPrice;

    /** Tổng số lượng máy (stock). */
    private Integer totalStock;

    /** Số lượng máy cho thuê. */
    private Integer rentalStock;

    /** Số lượng đã được đặt (đang thuê + chờ giao). */
    private Integer rented;

    /** Số lượng còn lại có thể thuê. */
    private Integer available;

    /** Tỷ lệ occupancy (%). */
    private Integer occupancyPercent;

    /** Trạng thái hoạt động. */
    private Boolean isActive;
}
