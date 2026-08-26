package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.AdminChatSendRequest;
import com.khoaluan.digishop.dto.ChatMessageDto;
import com.khoaluan.digishop.dto.ChatSendRequest;
import com.khoaluan.digishop.entity.NotificationType;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.service.ChatService;
import com.khoaluan.digishop.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * UC-31: xử lý tin nhắn realtime qua STOMP.
 * Danh tính người gửi được đọc ưu tiên từ native header "X-User-Id" (do StompAuthChannelInterceptor
 * gắn vào ngay trên chính frame SEND này) — KHÔNG dùng Principal do tham số @MessageMapping làm
 * nguồn chính, vì Principal gắn lúc CONNECT không "bám" lại đáng tin cậy vào các frame SEND sau đó
 * trong môi trường này. Principal vẫn được giữ làm phương án dự phòng.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Khách hàng gửi tin nhắn cho luồng hội thoại của chính họ. */
    @MessageMapping("/chat.customer")
    public void handleCustomerMessage(
            @Payload ChatSendRequest req,
            @Header(value = "X-User-Id", required = false) String userIdHeader,
            Principal principal
    ) {
        Long customerId = resolveUserId(userIdHeader, principal);
        ChatMessageDto saved = chatService.saveCustomerMessage(customerId, req.content(), req.fileUrl());

        // Đẩy cho hộp thư chung của mọi admin đang mở trang hỗ trợ.
        messagingTemplate.convertAndSend("/topic/admin/chat", saved);
        // Echo lại cho chính khách hàng (đồng bộ nếu họ mở nhiều tab) — dùng topic riêng theo userId
        // thay vì convertAndSendToUser, vì đường /user/... cần Spring định tuyến đúng theo Principal
        // của phiên STOMP, thứ đang không ổn định trong môi trường này (tin nhắn lưu DB được nhưng
        // không tới real-time, phải tải lại trang mới thấy). Topic không phụ thuộc Principal.
        messagingTemplate.convertAndSend("/topic/chat." + customerId, saved);
    }

    /** Admin/Sales Staff trả lời một khách hàng cụ thể. */
    @MessageMapping("/chat.admin")
    public void handleAdminMessage(
            @Payload AdminChatSendRequest req,
            @Header(value = "X-User-Id", required = false) String userIdHeader,
            Principal principal
    ) {
        Long adminId = resolveUserId(userIdHeader, principal);
        ChatMessageDto saved = chatService.saveAdminMessage(adminId, req.targetUserId(), req.content(), req.fileUrl());

        // Gửi cho khách hàng đang chờ.
        messagingTemplate.convertAndSend("/topic/chat." + req.targetUserId(), saved);
        // Đồng bộ lại cho các admin khác (và chính admin đang trả lời, nếu mở nhiều tab).
        messagingTemplate.convertAndSend("/topic/admin/chat", saved);
        // Thông báo trong-app để khách vẫn thấy có tin nhắn mới dù đang đóng khung chat.
        notificationService.create(req.targetUserId(), "Tin nhắn mới từ DigiShop",
                saved.content(), NotificationType.CHAT_MESSAGE, null);
    }

    private Long resolveUserId(String userIdHeader, Principal principal) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return Long.valueOf(userIdHeader);
        }
        if (principal != null && principal.getName() != null) {
            return Long.valueOf(principal.getName());
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "CHAT_UNAUTHENTICATED",
                "Không xác định được người gửi — vui lòng tải lại trang và đăng nhập lại.");
    }
}