package com.khoaluan.digishop.config;

import com.khoaluan.digishop.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * UC-31: Chat hỗ trợ trực tuyến qua WebSocket/STOMP.
 * - Endpoint bắt tay: /ws (WebSocket thuần, không SockJS — trình duyệt hiện đại đều hỗ trợ WS gốc)
 * - Client gửi tin nhắn tới /app/chat.customer hoặc /app/chat.admin (xem ChatController)
 * - Server đẩy tin về:
 *     /topic/admin/chat        -> hộp thư chung cho mọi admin (mọi tin nhắn mới)
 *     /user/queue/chat         -> tin nhắn riêng cho từng người dùng (customer nhận trả lời)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:5173");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}