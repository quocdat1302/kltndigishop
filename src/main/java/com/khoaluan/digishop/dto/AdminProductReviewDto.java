package com.khoaluan.digishop.dto;

import java.time.Instant;

/**
 * Đánh giá của khách (kèm ảnh) dùng cho trang admin "Quản lý đánh giá".
 * alreadyPublished = true nghĩa là đánh giá này đã được admin chọn đăng lên trang Feedback rồi.
 */
public record AdminProductReviewDto(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        String imageUrl,
        Instant createdAt,
        boolean alreadyPublished
) {
}