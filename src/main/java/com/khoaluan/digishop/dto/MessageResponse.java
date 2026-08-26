package com.khoaluan.digishop.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record MessageResponse(
        String message,
        Map<String, Object> details
) {
}
