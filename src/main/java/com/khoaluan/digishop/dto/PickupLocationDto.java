package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

public record PickupLocationDto(
        Long id,
        String name,
        String address,
        BigDecimal fee,
        boolean isDelivery,
        boolean active
) {
}