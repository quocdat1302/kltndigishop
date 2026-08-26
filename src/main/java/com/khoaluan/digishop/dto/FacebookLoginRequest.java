package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

public record FacebookLoginRequest(
        @NotBlank String accessToken,
        String userID
) {
}
