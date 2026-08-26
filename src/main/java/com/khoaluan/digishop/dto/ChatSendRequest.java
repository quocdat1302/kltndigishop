package com.khoaluan.digishop.dto;

/** Khách hàng gửi tin nhắn — luôn thuộc về luồng hội thoại của chính họ. */
public record ChatSendRequest(
        String content,
        String fileUrl
) {
}