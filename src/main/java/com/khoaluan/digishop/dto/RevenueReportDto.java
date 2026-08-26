package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueReportDto(
        BigDecimal totalRevenue,
        BigDecimal purchaseRevenue,
        BigDecimal rentalRevenue,
        long totalOrders,
        List<MonthlyRevenueDto> monthly
) {
    public record MonthlyRevenueDto(
            String month, // "yyyy-MM"
            BigDecimal purchaseRevenue,
            BigDecimal rentalRevenue,
            long orderCount
    ) {
    }
}