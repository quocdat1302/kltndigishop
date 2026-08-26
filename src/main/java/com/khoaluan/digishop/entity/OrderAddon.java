package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Snapshot 1 phụ kiện bổ sung mà khách đã chọn (hoặc được đi kèm miễn phí) cho 1 đơn thuê cụ thể. */
@Entity
@Table(name = "order_addons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Tên phụ kiện tại thời điểm đặt — snapshot, không đổi dù sau này ProductAddon gốc bị sửa/xoá. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean included;
}