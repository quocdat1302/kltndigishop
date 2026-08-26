package com.khoaluan.digishop.security;

import java.security.Principal;

/**
 * Principal tối giản cho phiên STOMP — name là userId dạng String, dùng để Spring định tuyến
 * tin nhắn tới đúng người qua /user/{name}/queue/... (convertAndSendToUser).
 */
public record StompPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}