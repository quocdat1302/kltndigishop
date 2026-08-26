package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByUserIdAndOrderTypeOrderByCreatedAtDesc(Long userId, OrderType orderType);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByOrderTypeOrderByCreatedAtDesc(OrderType orderType);

    boolean existsByOrderCode(String orderCode);

    Optional<Order> findByOrderCode(String orderCode);

    /** Đơn thuê đang trong tay khách (DELIVERED), đến hạn trả vào đúng ngày chỉ định, chưa được nhắc. */
    List<Order> findByOrderTypeAndStatusAndRentalEndDateAndDueReminderSentAtIsNull(
            OrderType orderType, OrderStatus status, LocalDate rentalEndDate);

    /** Toàn bộ đơn thuê đang trong tay khách (DELIVERED) và đã quá hạn trả — dùng để tính lại phạt mỗi ngày. */
    List<Order> findByOrderTypeAndStatusAndRentalEndDateBefore(
            OrderType orderType, OrderStatus status, LocalDate beforeDate);
}