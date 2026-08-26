package com.khoaluan.digishop.dto;

import java.time.Instant;

public record ChatMessageDto(
        Long id,
        Long conversationUserId,
        Long senderId,
        String senderName,
        String senderRole,
        String content,
        String fileUrl,
        Instant createdAt
) {
}