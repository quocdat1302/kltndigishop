package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.CartItem;
import com.khoaluan.digishop.entity.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CartItem> findByUserIdAndOrderTypeOrderByCreatedAtDesc(Long userId, OrderType orderType);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    /** Dùng để gộp số lượng khi thêm cùng 1 sản phẩm + cùng loại (mua) vào giỏ. */
    Optional<CartItem> findByUserIdAndProductIdAndOrderType(Long userId, Long productId, OrderType orderType);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);

    void deleteByIdInAndUserId(List<Long> ids, Long userId);
}
