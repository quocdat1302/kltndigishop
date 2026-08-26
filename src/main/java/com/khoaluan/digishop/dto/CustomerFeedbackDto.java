package com.khoaluan.digishop.dto;

import java.time.Instant;

public record CustomerFeedbackDto(
        Long id,
        String customerName,
        String comment,
        Integer rating,
        String imageUrl,
        Long productId,
        String productName,
        String status,
        long likeCount,
        boolean likedByMe,
        Instant createdAt,
        Long sourceReviewId
) {
}