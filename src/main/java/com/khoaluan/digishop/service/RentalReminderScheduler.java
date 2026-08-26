package com.khoaluan.digishop.service;

import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.entity.NotificationType;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.repository.OrderRepository;
import com.khoaluan.digishop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * UC-30: Nhắc khách trước ngày hết hạn thuê thiết bị.
 * Chạy mỗi ngày lúc 9:00 sáng (giờ VN), tìm các đơn thuê đang DELIVERED có rentalEndDate = ngày mai
 * và chưa từng được nhắc, rồi gửi email nhắc trả máy.
 *
 * UC-31: Nhắc khách về đơn thuê quá hạn trả máy.
 * Chạy mỗi ngày lúc 10:00 sáng (giờ VN), tìm các đơn thuê đang DELIVERED có rentalEndDate < ngày hôm nay
 * và chưa từng được nhắc quá hạn, rồi gửi email cảnh báo quá hạn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalReminderScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendDueTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now(VN_ZONE).plusDays(1);
        List<Order> dueSoon = orderRepository.findByOrderTypeAndStatusAndRentalEndDateAndDueReminderSentAtIsNull(
                OrderType.RENTAL, OrderStatus.DELIVERED, tomorrow);

        if (dueSoon.isEmpty()) {
            log.info("Rental due-reminder job: no orders due on {}", tomorrow);
            return;
        }

        log.info("Rental due-reminder job: sending {} reminder(s) for orders due on {}", dueSoon.size(), tomorrow);
        for (Order order : dueSoon) {
            // Đọc các field LAZY (user, items) ngay tại đây trong lúc session/transaction còn mở,
            // rồi mới truyền dữ liệu thuần (không phải entity) sang EmailService chạy @Async.
            String email = order.getUser() != null ? order.getUser().getEmail() : null;
            List<OrderEmailData.Line> items = order.getItems().stream()
                    .map(i -> new OrderEmailData.Line(i.getProductName(), i.getQuantity(), i.getSubtotal()))
                    .toList();

            emailService.sendRentalReturnReminderEmail(email, order.getOrderCode(), order.getRentalEndDate(), items);
            if (order.getUser() != null) {
                notificationService.create(order.getUser().getId(), "Sắp đến hạn trả máy",
                        "Đơn thuê " + order.getOrderCode() + " sẽ đến hạn trả vào ngày " + order.getRentalEndDate() + ".",
                        NotificationType.RENTAL_REMINDER, order.getId());
            }
            order.setDueReminderSentAt(Instant.now());
            orderRepository.save(order);
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendOverdueReminders() {
        LocalDate today = LocalDate.now(VN_ZONE);

        // Toàn bộ đơn đang trễ hạn (kể cả những đơn đã từng được nhắc trước đó) — để tính lại phạt
        // luỹ kế mỗi ngày. Chỉ đơn nào CHƯA từng nhắc (overdueReminderSentAt == null) mới gửi
        // email + notification lần đầu; các đơn đã nhắc rồi thì chỉ âm thầm cập nhật lại số tiền phạt.
        List<Order> overdue = orderRepository.findByOrderTypeAndStatusAndRentalEndDateBefore(
                OrderType.RENTAL, OrderStatus.DELIVERED, today);

        if (overdue.isEmpty()) {
            log.info("Rental overdue job: no overdue orders as of {}", today);
            return;
        }

        List<User> admins = userRepository.findByRole(Role.ADMIN);
        int firstTimeCount = 0;

        for (Order order : overdue) {
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(order.getRentalEndDate(), today);

            // Đơn giá phạt/ngày = coi như thuê thêm 1 ngày nữa theo đúng giá thuê trung bình/ngày của
            // đơn (subtotalAmount / số ngày thuê ban đầu). Nếu thiếu dữ liệu ngày thuê thì bỏ qua tính phạt.
            java.math.BigDecimal lateFee = java.math.BigDecimal.ZERO;
            if (order.getRentalStartDate() != null) {
                long plannedDays = Math.max(1,
                        java.time.temporal.ChronoUnit.DAYS.between(order.getRentalStartDate(), order.getRentalEndDate()));
                java.math.BigDecimal dailyRate = order.getSubtotalAmount()
                        .divide(java.math.BigDecimal.valueOf(plannedDays), 0, java.math.RoundingMode.HALF_UP);
                lateFee = dailyRate.multiply(java.math.BigDecimal.valueOf(overdueDays));
            }
            order.setLateFeeAmount(lateFee);

            boolean isFirstTime = order.getOverdueReminderSentAt() == null;
            if (isFirstTime) {
                firstTimeCount++;
                String email = order.getUser() != null ? order.getUser().getEmail() : null;
                List<OrderEmailData.Line> items = order.getItems().stream()
                        .map(i -> new OrderEmailData.Line(i.getProductName(), i.getQuantity(), i.getSubtotal()))
                        .toList();

                emailService.sendRentalOverdueReminderEmail(email, order.getOrderCode(), order.getRentalEndDate(), items);

                if (order.getUser() != null) {
                    notificationService.create(order.getUser().getId(), "Cảnh báo: Đơn thuê quá hạn — phát sinh phí phạt",
                            "Đơn thuê " + order.getOrderCode() + " đã quá hạn trả (hết hạn " + order.getRentalEndDate()
                                    + "). Phí phạt trễ hạn sẽ được tính " + formatVnd(lateFee) + "/ngày và tăng dần "
                                    + "cho đến khi bạn trả máy. Vui lòng trả máy ngay để tránh phát sinh thêm phí.",
                            NotificationType.RENTAL_REMINDER, order.getId());
                }

                for (User admin : admins) {
                    notificationService.create(admin.getId(), "Đơn thuê quá hạn: " + order.getOrderCode(),
                            "Khách " + order.getRecipientName() + " (" + order.getRecipientPhone()
                                    + ") quá hạn trả máy " + overdueDays + " ngày (hết hạn " + order.getRentalEndDate()
                                    + "). Phí phạt tạm tính: " + formatVnd(lateFee) + ". Cọc: "
                                    + formatVnd(order.getDepositAmount()) + ".",
                            NotificationType.OVERDUE_RENTAL, order.getId());
                }

                order.setOverdueReminderSentAt(Instant.now());
            }

            orderRepository.save(order);
        }

        log.info("Rental overdue job: {} order(s) currently overdue, {} first-time notification(s) sent",
                overdue.size(), firstTimeCount);
    }

    private static String formatVnd(java.math.BigDecimal amount) {
        if (amount == null) return "0đ";
        return String.format("%,dđ", amount.longValue());
    }
}