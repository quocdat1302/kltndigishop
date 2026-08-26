package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.OrderItem;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Dùng để kiểm tra trùng lịch thuê: các order item đang "chiếm dụng" một sản phẩm ở các trạng thái chưa kết thúc. */
    List<OrderItem> findByProduct_IdAndOrder_OrderTypeAndOrder_StatusIn(
            Long productId, OrderType orderType, Collection<OrderStatus> statuses);

    /** UC: chỉ cho phép đánh giá sản phẩm đã thực sự mua/thuê xong ("verified purchase"). */
    boolean existsByProduct_IdAndOrder_User_IdAndOrder_StatusIn(
            Long productId, Long userId, Collection<OrderStatus> statuses);
}