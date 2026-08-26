package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 1 lựa chọn nhận máy khi thuê: có thể là "nhận tại shop" (miễn phí), "nhận tại chi nhánh khác"
 * (phụ phí cố định), hoặc "giao tận nơi" (phụ phí, có thể kèm ghi chú phạm vi áp dụng).
 * Thay cho model cũ chỉ có đúng 2 lựa chọn cứng (PICKUP_AT_SHOP / HOME_DELIVERY).
 */
@Entity
@Table(name = "pickup_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Địa chỉ cụ thể, hoặc ghi chú kiểu "Giao tận nơi trong bán kính 5km". */
    private String address;

    @Column(nullable = false)
    private BigDecimal fee;

    /** true = đây là hình thức giao tận nơi (cần nhập địa chỉ khách); false = khách tự đến điểm cố định. */
    @Column(name = "is_delivery", nullable = false)
    private boolean isDelivery;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order")
    private Integer displayOrder;
}