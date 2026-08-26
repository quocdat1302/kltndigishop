package com.khoaluan.digishop.dto;

import java.time.Instant;

public record ProductReviewDto(
        Long id,
        Long productId,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        String imageUrl,
        Instant createdAt
) {
}