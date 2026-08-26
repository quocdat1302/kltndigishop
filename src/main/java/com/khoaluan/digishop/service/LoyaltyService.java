package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.LoyaltyInfoDto;
import com.khoaluan.digishop.entity.LoyaltyTier;
import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Hạng "khách hàng thân thiết" — tính tự động từ lịch sử đơn hàng đã hoàn tất (COMPLETED), không cần
 * admin gắn tay. Đạt ngưỡng chi tiêu HOẶC ngưỡng số đơn là lên hạng, kèm giảm giá tự động ở checkout
 * (xem OrderService#checkoutPurchase/checkoutRental).
 */
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    /** Ngưỡng tổng chi tiêu (đơn COMPLETED) để lên hạng thân thiết. */
    private static final BigDecimal SPEND_THRESHOLD = BigDecimal.valueOf(5_000_000);
    /** Ngưỡng số đơn COMPLETED để lên hạng thân thiết (đạt 1 trong 2 ngưỡng là đủ). */
    private static final long ORDER_COUNT_THRESHOLD = 5;
    /** Mức giảm giá tự động áp dụng cho khách hàng thân thiết ở mỗi đơn mới. */
    private static final BigDecimal LOYAL_DISCOUNT_PERCENT = BigDecimal.valueOf(5);

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public LoyaltyInfoDto getLoyaltyInfo(Long userId) {
        List<Order> completed = orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .toList();

        BigDecimal totalSpent = completed.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long orderCount = completed.size();

        boolean loyal = totalSpent.compareTo(SPEND_THRESHOLD) >= 0 || orderCount >= ORDER_COUNT_THRESHOLD;
        LoyaltyTier tier = loyal ? LoyaltyTier.THAN_THIET : LoyaltyTier.NONE;

        BigDecimal amountToNextTier = BigDecimal.ZERO;
        if (!loyal) {
            amountToNextTier = SPEND_THRESHOLD.subtract(totalSpent).max(BigDecimal.ZERO);
        }

        return LoyaltyInfoDto.builder()
                .tier(tier)
                .totalSpent(totalSpent)
                .completedOrderCount(orderCount)
                .discountPercent(loyal ? LOYAL_DISCOUNT_PERCENT : BigDecimal.ZERO)
                .amountToNextTier(amountToNextTier)
                .build();
    }

    /** Phần trăm giảm giá tự động áp dụng cho user này ở đơn hàng mới — 0 nếu chưa đạt hạng thân thiết. */
    @Transactional(readOnly = true)
    public BigDecimal getAutoDiscountPercent(Long userId) {
        return getLoyaltyInfo(userId).discountPercent();
    }

    /** Tính số tiền giảm từ % và subtotal, làm tròn 2 chữ số như các phép tính tiền khác trong hệ thống. */
    public static BigDecimal calcDiscountAmount(BigDecimal subtotal, BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return subtotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}