package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PromotionDto(
        Long id,
        String title,
        String description,
        String code,
        BigDecimal discountPercent,
        Instant startDate,
        Instant endDate,
        Boolean isActive
) {
}
