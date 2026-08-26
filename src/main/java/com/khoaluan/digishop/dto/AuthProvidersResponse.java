package com.khoaluan.digishop.dto;

import lombok.Builder;

@Builder
public record AuthProvidersResponse(
        boolean googleEnabled,
        boolean facebookEnabled
) {
}
