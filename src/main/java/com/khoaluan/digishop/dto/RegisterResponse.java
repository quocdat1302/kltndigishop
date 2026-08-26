package com.khoaluan.digishop.dto;

import lombok.Builder;

@Builder
public record RegisterResponse(
        Long userId,
        String message,
        String maskedEmail,
        boolean requiresVerification,
        String otpCode,
        long otpExpiresIn,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        UserDto user
) {
}
