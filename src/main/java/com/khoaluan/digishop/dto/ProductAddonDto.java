package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

public record ProductAddonDto(
        Long id,
        Long productId,
        String name,
        BigDecimal price,
        boolean included
) {
}