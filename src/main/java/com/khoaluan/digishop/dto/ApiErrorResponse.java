package com.khoaluan.digishop.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Object details
) {
}
