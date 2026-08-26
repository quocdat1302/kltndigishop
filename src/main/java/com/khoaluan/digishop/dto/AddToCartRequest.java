package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.OrderType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddToCartRequest(
        @NotNull(message = "productId không được để trống")
        Long productId,

        @NotNull(message = "orderType không được để trống")
        OrderType orderType,

        @NotNull(message = "quantity không được để trống")
        @Min(value = 1, message = "quantity phải lớn hơn 0")
        Integer quantity,

        /** Bắt buộc khi orderType = RENTAL. */
        @FutureOrPresent(message = "Ngày bắt đầu thuê không được ở quá khứ")
        LocalDate rentalStartDate,

        /** Bắt buộc khi orderType = RENTAL. */
        @FutureOrPresent(message = "Ngày kết thúc thuê không được ở quá khứ")
        LocalDate rentalEndDate
) {
}