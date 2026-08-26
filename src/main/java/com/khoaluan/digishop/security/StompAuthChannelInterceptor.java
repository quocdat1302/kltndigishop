package com.khoaluan.digishop.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Xác thực JWT cho MỌI frame STOMP có kèm header "Authorization" (không chỉ lúc CONNECT).
 *
 * Trước đây chỉ xác thực lúc CONNECT rồi gắn Principal cho cả phiên — nhưng thực tế Principal đó
 * không "bám" lại được vào các frame SEND sau đó trong môi trường này (nguyên nhân chưa xác định
 * chắc chắn — có thể do khác biệt hành vi giữa các bản Spring/STOMP client), khiến @MessageMapping
 * luôn nhận Principal = null dù CONNECT đã xác thực thành công. Để chắc chắn, giờ xác thực lại
 * ngay trên từng frame gửi tin nhắn, rồi gắn kết quả vào 1 native header riêng ("X-User-Id") mà
 * ChatController đọc trực tiếp bằng @Header, không phụ thuộc cơ chế Principal của Spring nữa.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        // Chuẩn STOMP 1.2 cho phép frame đầu tiên là CONNECT hoặc STOMP — @stomp/stompjs bản mới
        // (v7+) mặc định gửi "STOMP" chứ không phải "CONNECT".
        boolean isConnectFrame = StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command);
        boolean isSendFrame = StompCommand.SEND.equals(command);

        if (!isConnectFrame && !isSendFrame) {
            return message; // SUBSCRIBE, DISCONNECT, ACK... không cần xác thực lại ở đây
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (isConnectFrame) {
            // CONNECT bắt buộc phải có token hợp lệ — không cho kết nối nếu thiếu/sai.
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Missing or invalid Authorization header");
            }
        } else if (authHeader == null) {
            // Frame SEND không kèm Authorization (client cũ chưa cập nhật) — bỏ qua, để Principal
            // của phiên (nếu có) tự xử lý như cơ chế cũ, tránh chặn nhầm.
            return message;
        }

        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            throw new MessagingException("Invalid or expired token");
        }
        Long userId = jwtService.extractUserId(token);
        accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        accessor.setNativeHeader("X-User-Id", String.valueOf(userId));

        // QUAN TRỌNG: StompHeaderAccessor.wrap() không tự ghi thay đổi ngược lại vào `message` gốc —
        // phải dựng message MỚI từ accessor đã sửa (accessor.getMessageHeaders()), nếu không mọi
        // thay đổi ở trên (Principal, header X-User-Id) đều bị mất khi message tiếp tục đi xuống.
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
}