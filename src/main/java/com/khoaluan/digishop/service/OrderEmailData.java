package com.khoaluan.digishop.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu thuần (không phải entity JPA) dùng để gửi email đơn hàng.
 * EmailService chạy @Async trên thread khác — nếu truyền thẳng entity Order (có field LAZY như
 * user/items) vào đó thì có rủi ro LazyInitializationException vì session của transaction gốc
 * đã đóng. Nên luôn build sẵn record này trong lúc còn ở transaction gốc rồi mới gọi EmailService.
 */
public record OrderEmailData(
        String orderCode,
        boolean rental,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        Integer rentalDays,
        BigDecimal depositAmount,
        BigDecimal totalAmount,
        List<Line> items
) {
    public record Line(String productName, int quantity, BigDecimal subtotal) {
    }
}