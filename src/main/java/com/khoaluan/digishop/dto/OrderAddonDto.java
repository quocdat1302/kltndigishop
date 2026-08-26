package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

public record OrderAddonDto(
        Long id,
        String name,
        BigDecimal price,
        boolean included
) {
}