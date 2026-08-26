package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DamagedDeviceDto(
        Long productId,
        String productName,
        String brand,
        long disputeCount,
        BigDecimal totalDamageAmount,
        Instant lastDisputeAt
) {
}