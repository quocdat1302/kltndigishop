package com.khoaluan.digishop.dto;

import lombok.Builder;

@Builder
public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn
) {
}
