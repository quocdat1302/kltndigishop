package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Giữ tham chiếu sản phẩm gốc (có thể null nếu sản phẩm bị xoá về sau). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Snapshot tên sản phẩm tại thời điểm đặt hàng. */
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_image_url")
    private String productImageUrl;

    /** Đơn giá tại thời điểm đặt hàng (giá mua hoặc giá thuê/ngày). */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    /** Chỉ dùng cho đơn thuê. */
    @Column(name = "rental_days")
    private Integer rentalDays;

    /** Chỉ dùng khi thuê theo buổi (thay vì theo ngày): "MORNING" | "NOON" | "AFTERNOON" | "EVENING", null nếu thuê theo ngày. */
    @Column(name = "rental_slot")
    private String rentalSlot;

    /** Mua: unitPrice * quantity. Thuê: unitPrice * quantity * rentalDays. */
    @Column(nullable = false)
    private BigDecimal subtotal;
}
