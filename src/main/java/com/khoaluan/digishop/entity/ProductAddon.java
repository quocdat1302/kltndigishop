package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Phụ kiện bổ sung khi thuê 1 sản phẩm (vd: cuộn phim, pin dự phòng, dây đeo...).
 * included = true: đi kèm miễn phí, hiển thị "Incl." và tự động cộng vào đơn.
 * included = false: khách tự chọn thêm, cộng giá vào tổng tiền thuê nếu được chọn.
 */
@Entity
@Table(name = "product_addons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    /** Giá phụ thu — bằng 0 nếu included = true (đi kèm miễn phí). */
    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean included;

    /** Thứ tự hiển thị trong danh sách, số nhỏ hiện trước. */
    @Column(name = "display_order")
    private Integer displayOrder;
}