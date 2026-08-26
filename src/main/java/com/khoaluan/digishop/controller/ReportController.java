package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.DamagedDeviceDto;
import com.khoaluan.digishop.dto.OverdueRentalDto;
import com.khoaluan.digishop.dto.RevenueReportDto;
import com.khoaluan.digishop.dto.TopRentedProductDto;
import com.khoaluan.digishop.dto.WeeklyRevenueDto;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** GET /api/admin/reports/revenue?from=&to=&type= — báo cáo doanh thu theo loại (mua/thuê). */
    @GetMapping("/revenue")
    public RevenueReportDto getRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OrderType type
    ) {
        return reportService.getRevenueReport(from, to, type);
    }

    /** GET /api/admin/reports/revenue/weekly — doanh thu 7 ngày gần nhất, theo từng ngày. */
    @GetMapping("/revenue/weekly")
    public WeeklyRevenueDto getWeeklyRevenue() {
        return reportService.getWeeklyRevenue();
    }

    /** GET /api/admin/reports/top-rented-products — sản phẩm được thuê nhiều nhất. */
    @GetMapping("/top-rented-products")
    public List<TopRentedProductDto> getTopRentedProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return reportService.getTopRentedProducts(from, to, limit);
    }

    /** GET /api/admin/reports/damaged-devices — danh sách thiết bị hư hỏng. */
    @GetMapping("/damaged-devices")
    public List<DamagedDeviceDto> getDamagedDevices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.getDamagedDevices(from, to);
    }

    /** GET /api/admin/reports/overdue-rentals — danh sách đơn thuê quá hạn chưa trả. */
    @GetMapping("/overdue-rentals")
    public List<OverdueRentalDto> getOverdueRentals() {
        return reportService.getOverdueRentals();
    }
}