package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UC-31: tin nhắn hỗ trợ trực tuyến. Mỗi khách hàng có đúng 1 luồng hội thoại với "Sales
 * Staff/Admin" (không phân theo đơn hàng) — conversationUserId luôn là id của khách hàng, dù
 * người gửi (senderId) là chính khách hàng đó hay là admin đang trả lời họ.
 */
@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** id của khách hàng sở hữu luồng hội thoại này (dù ai là người gửi tin nhắn này). */
    @Column(name = "conversation_user_id", nullable = false)
    private Long conversationUserId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private Role senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** URL của file/ảnh được gửi kèm (nếu có). Có thể chứa một hoặc nhiều URL nối bằng dấu phẩy. */
    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}