package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status không được để trống")
        OrderStatus status
) {
}
