package com.khoaluan.digishop.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Tình trạng còn máy của 1 sản phẩm trong 1 ngày cụ thể — dùng để tô màu lịch chọn ngày ở trang đặt thuê,
 * để khách thấy ngay ngày nào đã kín máy thay vì bấm thử rồi mới biết.
 */
public record DayAvailabilityDto(
        LocalDate date,
        int reservedQuantity,
        int remaining,
        /** Các buổi (MORNING/NOON/AFTERNOON/EVENING) đã có người đặt trong ngày này, nếu sản phẩm hỗ trợ thuê theo buổi. */
        List<String> bookedSlots
) {
}
