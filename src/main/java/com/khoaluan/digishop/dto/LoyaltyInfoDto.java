package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.LoyaltyTier;
import lombok.Builder;

import java.math.BigDecimal;

/** Thông tin hạng khách hàng thân thiết — tính tự động, gắn kèm mỗi UserDto. */
@Builder
public record LoyaltyInfoDto(
        LoyaltyTier tier,
        BigDecimal totalSpent,
        long completedOrderCount,
        BigDecimal discountPercent,
        /** Còn thiếu bao nhiêu tiền để lên hạng thân thiết (0 nếu đã đạt hoặc đủ điều kiện theo số đơn). */
        BigDecimal amountToNextTier
) {
}