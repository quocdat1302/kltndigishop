package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DeductDepositRequest(
        @NotNull(message = "Vui lòng nhập số tiền trừ cọc")
        @DecimalMin(value = "0.01", message = "Số tiền trừ cọc phải lớn hơn 0")
        BigDecimal damageAmount,

        @NotBlank(message = "Vui lòng nhập lý do tranh chấp")
        String disputeReason
) {
}