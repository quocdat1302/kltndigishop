package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.util.List;

/** UC-29: Báo cáo doanh thu 7 ngày gần nhất, theo từng ngày, phục vụ biểu đồ tuần ở Dashboard/Reports. */
public record WeeklyRevenueDto(
        BigDecimal totalRevenue,
        BigDecimal purchaseRevenue,
        BigDecimal rentalRevenue,
        long totalOrders,
        List<DailyRevenueDto> daily
) {
    public record DailyRevenueDto(
            String date,     // "yyyy-MM-dd"
            String dayLabel, // "T2", "T3", ..., "CN"
            BigDecimal purchaseRevenue,
            BigDecimal rentalRevenue,
            long orderCount
    ) {
    }
}