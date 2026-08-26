package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Hợp đồng thuê đã được khách ký (bước gác trước khi đơn chính thức chuyển DEPOSIT_PAID).
 * contractText là snapshot nội dung tại thời điểm ký — không đổi về sau dù mẫu hợp đồng có cập nhật,
 * để tránh tranh chấp "hợp đồng khác với lúc ký".
 */
@Entity
@Table(name = "rental_contracts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "contract_text", columnDefinition = "TEXT", nullable = false)
    private String contractText;

    /** Ảnh chữ ký dạng base64 data URL (vd "data:image/png;base64,..."), vẽ từ canvas trên trình duyệt. */
    @Column(name = "signature_data_url", columnDefinition = "TEXT", nullable = false)
    private String signatureDataUrl;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @PrePersist
    void prePersist() {
        if (signedAt == null) signedAt = Instant.now();
    }
}