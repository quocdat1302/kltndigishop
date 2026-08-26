package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Ảnh mẫu chụp bằng máy này (feedback/demo), hiển thị ở trang sản phẩm để khách hình dung chất lượng ảnh. */
@Entity
@Table(name = "product_sample_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSamplePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    /** Chú thích ngắn, vd "Chụp bởi khách thuê, buổi tối tại Hồ Gươm". */
    private String caption;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}