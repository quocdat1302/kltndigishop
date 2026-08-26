package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExtendRentalRequest(
        @NotNull(message = "Vui lòng chọn ngày kết thúc mới")
        @Future(message = "Ngày kết thúc mới phải ở tương lai")
        LocalDate newEndDate
) {
}