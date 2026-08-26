package com.khoaluan.digishop.dto;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String title,
        String message,
        String type,
        Long relatedOrderId,
        boolean isRead,
        Instant createdAt
) {
}