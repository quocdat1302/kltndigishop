package com.khoaluan.digishop.dto;

/**
 * Bước kiểm tra tình trạng máy lúc nhận lại (RENTAL_RETURNED -> INSPECTED).
 * Quyết định hoàn/trừ cọc được tách thành 2 API riêng: xem DeductDepositRequest
 * (PUT .../deposit/deduct) và PUT .../deposit/refund (không cần body).
 */
public record InspectReturnRequest(
        String inspectionNote
) {
}