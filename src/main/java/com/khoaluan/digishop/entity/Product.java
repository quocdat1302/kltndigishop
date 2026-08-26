package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String type; // DSLR, Mirrorless, Lens, Phụ kiện

    @Column(nullable = false)
    private BigDecimal buyPrice;

    @Column(nullable = false)
    private BigDecimal rentPrice;

    /** Giá thuê theo tuần (tuỳ chọn) — null nghĩa là chưa cấu hình, chỉ áp dụng giá theo ngày. */
    @Column(name = "rent_price_weekly")
    private BigDecimal rentPriceWeekly;

    /** Giá thuê theo khung giờ trong ngày (tuỳ chọn), song song với giá theo ngày ở trên — null nghĩa là chưa cấu hình. */
    @Column(name = "rent_price_morning")
    private BigDecimal rentPriceMorning; // khung Sáng, vd 7h-12h

    @Column(name = "rent_price_afternoon")
    private BigDecimal rentPriceAfternoon; // khung Chiều, vd 12h30-17h30

    @Column(name = "rent_price_evening")
    private BigDecimal rentPriceEvening; // khung Tối, vd 18h-22h30

    /** Danh sách phụ kiện đi kèm khi thuê, hiển thị dạng text tự do, mỗi món 1 dòng hoặc phân tách bằng dấu phẩy. */
    @Column(name = "accessories_included", columnDefinition = "TEXT")
    private String accessoriesIncluded;

    /** Thông số kỹ thuật tự do, admin nhập mỗi dòng 1 mục dạng "Tên: Giá trị" (vd "Cảm biến: CMOS 1.0 inch"). */
    @Column(name = "tech_specs", columnDefinition = "TEXT")
    private String techSpecs;

    /** Ngàm ống kính (chỉ áp dụng cho máy ảnh/ống kính), ví dụ: Leica M-mount, Canon RF, Sony E... */
    @Column(name = "lens_mount")
    private String lensMount;

    private String imageUrl;

    private String description;

    @Column(nullable = false)
    private Integer stockQuantity;

    /** Số lượng máy có sẵn cho thuê (riêng biệt với mua). Nếu null, dùng chung với stockQuantity. */
    @Column(name = "rental_stock_quantity")
    private Integer rentalStockQuantity;

    @Column(nullable = false)
    private Boolean isAvailable;

    @Column(name = "product_condition", nullable = false)
    private String productCondition; // new, used

    @Column(nullable = false)
    private Boolean isNew;

    @Column(nullable = false)
    private Boolean isHot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}