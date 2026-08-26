package com.khoaluan.digishop.entity;

/** Hạng khách hàng, tính tự động từ lịch sử đơn hàng đã hoàn tất — xem LoyaltyService. */
public enum LoyaltyTier {
    /** Khách thường, chưa đạt ngưỡng khách hàng thân thiết. */
    NONE,
    /** Khách hàng thân thiết — đủ điều kiện chi tiêu/số đơn, được giảm giá tự động khi thanh toán. */
    THAN_THIET
}