package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.DamagedDeviceDto;
import com.khoaluan.digishop.dto.OverdueRentalDto;
import com.khoaluan.digishop.dto.RevenueReportDto;
import com.khoaluan.digishop.dto.TopRentedProductDto;
import com.khoaluan.digishop.dto.WeeklyRevenueDto;
import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderItem;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UC-29: Báo cáo thống kê cho Admin. Gộp dữ liệu ở backend thay vì để FE tự tải hết đơn hàng về tính. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter MONTH_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderRepository orderRepository;

    /** Doanh thu mua/thuê, theo khoảng ngày [from, to] (dựa trên completedAt), có thể lọc theo loại đơn. */
    @Transactional(readOnly = true)
    public RevenueReportDto getRevenueReport(LocalDate from, LocalDate to, OrderType type) {
        List<Order> completed = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.DISPUTED)
                .filter(o -> o.getCompletedAt() != null)
                .filter(o -> isWithinRange(o.getCompletedAt(), from, to))
                .filter(o -> type == null || o.getOrderType() == type)
                .toList();

        BigDecimal purchaseTotal = BigDecimal.ZERO;
        BigDecimal rentalTotal = BigDecimal.ZERO;
        Map<String, Object[]> byMonth = new LinkedHashMap<>(); // month -> [purchase, rental, orderCount]

        for (Order o : completed) {
            BigDecimal amount = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            String monthKey = MONTH_KEY_FMT.format(o.getCompletedAt().atZone(VN_ZONE));
            Object[] bucket = byMonth.computeIfAbsent(monthKey, k -> new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, 0L});

            if (o.getOrderType() == OrderType.PURCHASE) {
                purchaseTotal = purchaseTotal.add(amount);
                bucket[0] = ((BigDecimal) bucket[0]).add(amount);
            } else {
                rentalTotal = rentalTotal.add(amount);
                bucket[1] = ((BigDecimal) bucket[1]).add(amount);
            }
            bucket[2] = (Long) bucket[2] + 1;
        }

        List<RevenueReportDto.MonthlyRevenueDto> monthly = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new RevenueReportDto.MonthlyRevenueDto(
                        e.getKey(), (BigDecimal) e.getValue()[0], (BigDecimal) e.getValue()[1], (Long) e.getValue()[2]))
                .toList();

        return new RevenueReportDto(purchaseTotal.add(rentalTotal), purchaseTotal, rentalTotal, completed.size(), monthly);
    }

    private static final DateTimeFormatter DAY_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] VN_WEEKDAY_LABEL = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    /** Doanh thu 7 ngày gần nhất (kể cả hôm nay), theo từng ngày — dựa trên completedAt, giống getRevenueReport
     *  nhưng luôn cố định cửa sổ 7 ngày và gộp cả số lượng đơn hoàn tất trong ngày để FE vẽ biểu đồ tuần. */
    @Transactional(readOnly = true)
    public WeeklyRevenueDto getWeeklyRevenue() {
        LocalDate today = LocalDate.now(VN_ZONE);
        LocalDate from = today.minusDays(6);

        List<Order> completed = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.DISPUTED)
                .filter(o -> o.getCompletedAt() != null)
                .filter(o -> isWithinRange(o.getCompletedAt(), from, today))
                .toList();

        // Khởi tạo sẵn đủ 7 ngày (kể cả ngày không có đơn nào) để biểu đồ không bị thiếu cột.
        Map<String, Object[]> byDay = new LinkedHashMap<>(); // day -> [purchase, rental, orderCount]
        for (int i = 0; i <= 6; i++) {
            LocalDate d = from.plusDays(i);
            byDay.put(DAY_KEY_FMT.format(d), new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, 0L});
        }

        BigDecimal purchaseTotal = BigDecimal.ZERO;
        BigDecimal rentalTotal = BigDecimal.ZERO;

        for (Order o : completed) {
            BigDecimal amount = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            LocalDate day = o.getCompletedAt().atZone(VN_ZONE).toLocalDate();
            String dayKey = DAY_KEY_FMT.format(day);
            Object[] bucket = byDay.get(dayKey);
            if (bucket == null) continue; // an toàn, không nên xảy ra vì đã lọc theo range

            if (o.getOrderType() == OrderType.PURCHASE) {
                purchaseTotal = purchaseTotal.add(amount);
                bucket[0] = ((BigDecimal) bucket[0]).add(amount);
            } else {
                rentalTotal = rentalTotal.add(amount);
                bucket[1] = ((BigDecimal) bucket[1]).add(amount);
            }
            bucket[2] = (Long) bucket[2] + 1;
        }

        List<WeeklyRevenueDto.DailyRevenueDto> daily = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object[]> e : byDay.entrySet()) {
            LocalDate d = from.plusDays(i++);
            Object[] bucket = e.getValue();
            String label = VN_WEEKDAY_LABEL[d.getDayOfWeek().getValue() - 1];
            daily.add(new WeeklyRevenueDto.DailyRevenueDto(
                    e.getKey(), label, (BigDecimal) bucket[0], (BigDecimal) bucket[1], (Long) bucket[2]));
        }

        long totalOrders = daily.stream().mapToLong(WeeklyRevenueDto.DailyRevenueDto::orderCount).sum();
        return new WeeklyRevenueDto(purchaseTotal.add(rentalTotal), purchaseTotal, rentalTotal, totalOrders, daily);
    }

    /** Thiết bị được thuê nhiều nhất trong khoảng [from, to] (dựa trên createdAt của đơn), bỏ qua đơn đã huỷ. */
    @Transactional(readOnly = true)
    public List<TopRentedProductDto> getTopRentedProducts(LocalDate from, LocalDate to, int limit) {
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getOrderType() == OrderType.RENTAL)
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> isWithinRange(o.getCreatedAt(), from, to))
                .toList();

        Map<Long, Object[]> agg = new LinkedHashMap<>(); // productId -> [count, quantity, revenue, name, brand]
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                if (item.getProduct() == null) continue;
                Long productId = item.getProduct().getId();
                Object[] bucket = agg.computeIfAbsent(productId, k -> new Object[]{
                        0L, 0L, BigDecimal.ZERO, item.getProductName(), item.getProduct().getBrand()
                });
                bucket[0] = (Long) bucket[0] + 1;
                bucket[1] = (Long) bucket[1] + item.getQuantity();
                bucket[2] = ((BigDecimal) bucket[2]).add(item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO);
            }
        }

        List<TopRentedProductDto> result = new ArrayList<>();
        agg.forEach((productId, bucket) -> result.add(new TopRentedProductDto(
                productId, (String) bucket[3], (String) bucket[4], (Long) bucket[0], (Long) bucket[1], (BigDecimal) bucket[2]
        )));

        return result.stream()
                .sorted(Comparator.comparingLong(TopRentedProductDto::rentalCount).reversed())
                .limit(limit)
                .toList();
    }

    /** Thiết bị hư hỏng — gộp từ các đơn DISPUTED trong khoảng [from, to] (dựa trên inspectedAt). */
    @Transactional(readOnly = true)
    public List<DamagedDeviceDto> getDamagedDevices(LocalDate from, LocalDate to) {
        List<Order> disputed = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() == OrderStatus.DISPUTED)
                .filter(o -> o.getInspectedAt() != null)
                .filter(o -> isWithinRange(o.getInspectedAt(), from, to))
                .toList();

        Map<Long, Object[]> agg = new LinkedHashMap<>(); // productId -> [count, totalDamage, lastAt, name, brand]
        for (Order o : disputed) {
            BigDecimal damage = o.getDamageAmount() != null ? o.getDamageAmount() : BigDecimal.ZERO;
            int itemCount = Math.max(o.getItems().size(), 1);
            BigDecimal sharePerItem = damage.divide(BigDecimal.valueOf(itemCount), 2, java.math.RoundingMode.HALF_UP);

            for (OrderItem item : o.getItems()) {
                if (item.getProduct() == null) continue;
                Long productId = item.getProduct().getId();
                Object[] bucket = agg.computeIfAbsent(productId, k -> new Object[]{
                        0L, BigDecimal.ZERO, o.getInspectedAt(), item.getProductName(), item.getProduct().getBrand()
                });
                bucket[0] = (Long) bucket[0] + 1;
                bucket[1] = ((BigDecimal) bucket[1]).add(sharePerItem);
                if (o.getInspectedAt().isAfter((Instant) bucket[2])) {
                    bucket[2] = o.getInspectedAt();
                }
            }
        }

        List<DamagedDeviceDto> result = new ArrayList<>();
        agg.forEach((productId, bucket) -> result.add(new DamagedDeviceDto(
                productId, (String) bucket[3], (String) bucket[4], (Long) bucket[0], (BigDecimal) bucket[1], (Instant) bucket[2]
        )));

        return result.stream()
                .sorted(Comparator.comparing(DamagedDeviceDto::totalDamageAmount).reversed())
                .toList();
    }

    /** UC-31: Danh sách đơn thuê quá hạn chưa được trả (TẤT CẢ đơn còn trễ, không chỉ đơn mới trễ lần đầu —
     *  trước đây lọc theo overdueReminderSentAt IS NULL nên đơn đã được nhắc 1 lần sẽ biến mất khỏi danh
     *  sách dù vẫn đang trễ, đây là lỗi hiển thị sai cho admin, đã sửa lại). */
    @Transactional(readOnly = true)
    public List<OverdueRentalDto> getOverdueRentals() {
        LocalDate today = LocalDate.now(VN_ZONE);
        List<Order> overdue = orderRepository.findByOrderTypeAndStatusAndRentalEndDateBefore(
                OrderType.RENTAL, OrderStatus.DELIVERED, today);

        List<OverdueRentalDto> result = new ArrayList<>();
        for (Order order : overdue) {
            if (order.getRentalEndDate() == null) continue;

            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(
                    order.getRentalEndDate(), today);

            String productNames = order.getItems().stream()
                    .map(OrderItem::getProductName)
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("N/A");

            int totalQty = order.getItems().stream()
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 1)
                    .sum();

            result.add(OverdueRentalDto.builder()
                    .orderCode(order.getOrderCode())
                    .orderId(order.getId())
                    .customerName(order.getRecipientName())
                    .customerPhone(order.getRecipientPhone())
                    .customerEmail(order.getUser() != null ? order.getUser().getEmail() : null)
                    .rentalEndDate(order.getRentalEndDate())
                    .overdueDays(overdueDays)
                    .productName(productNames)
                    .quantity(totalQty)
                    .depositAmount(order.getDepositAmount())
                    .overdueReminderSentAt(order.getOverdueReminderSentAt())
                    .lateFeeAmount(order.getLateFeeAmount())
                    .build());
        }

        return result.stream()
                .sorted(Comparator.comparing(OverdueRentalDto::getOverdueDays).reversed())
                .toList();
    }

    private boolean isWithinRange(Instant instant, LocalDate from, LocalDate to) {
        if (instant == null) return false;
        LocalDate date = instant.atZone(VN_ZONE).toLocalDate();
        if (from != null && date.isBefore(from)) return false;
        if (to != null && date.isAfter(to)) return false;
        return true;
    }
}