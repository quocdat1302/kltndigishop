package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "customer_feedbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String comment;

    @Column(nullable = false)
    private Integer rating;

    private String imageUrl;

    /** Sản phẩm (máy ảnh) được feedback này gắn vào — hiện dạng "Chụp bằng [tên máy]" trên card. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Snapshot tên sản phẩm tại thời điểm đăng — không đổi dù sau này sản phẩm bị xoá/đổi tên. */
    @Column(name = "product_name")
    private String productName;

    /**
     * Admin đăng trực tiếp nên mặc định APPROVED (hiện công khai ngay); admin có thể ẩn (REJECTED) hoặc xoá.
     * Không còn PENDING chờ khách gửi — khách không tự đăng feedback được nữa.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.APPROVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Nếu bài feedback này được đăng từ 1 đánh giá (ProductReview) khách đã gửi kèm ảnh,
     * lưu lại id đánh giá gốc để: (1) tránh đăng trùng 1 đánh giá 2 lần, (2) trang admin
     * "Quản lý đánh giá" biết đánh giá nào đã được đăng lên rồi. Null nếu admin tự gõ tay bài feedback.
     */
    @Column(name = "source_review_id")
    private Long sourceReviewId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}