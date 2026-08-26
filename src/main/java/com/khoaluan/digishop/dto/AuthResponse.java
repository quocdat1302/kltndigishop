package com.khoaluan.digishop.dto;

import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        UserDto user
) {
}
