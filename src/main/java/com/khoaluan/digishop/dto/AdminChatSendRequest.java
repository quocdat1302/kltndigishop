package com.khoaluan.digishop.dto;

/** Admin/Sales Staff trả lời một khách hàng cụ thể. */
public record AdminChatSendRequest(Long targetUserId, String content, String fileUrl) {
}