package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "quantity phải lớn hơn 0")
        Integer quantity,

        @FutureOrPresent(message = "Ngày bắt đầu thuê không được ở quá khứ")
        LocalDate rentalStartDate,

        @FutureOrPresent(message = "Ngày kết thúc thuê không được ở quá khứ")
        LocalDate rentalEndDate
) {
}