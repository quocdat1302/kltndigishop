package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.FulfillmentMethod;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrderDto(
        Long id,
        String orderCode,
        OrderType orderType,
        OrderStatus status,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        FulfillmentMethod fulfillmentMethod,
        String pickupLocationName,
        BigDecimal pickupFee,
        String note,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        Integer rentalDays,
        BigDecimal subtotalAmount,
        String promotionCode,
        BigDecimal discountAmount,
        BigDecimal loyaltyDiscountAmount,
        BigDecimal depositAmount,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant completedAt,
        String returnReason,
        List<String> returnImageUrls,
        Instant returnRequestedAt,
        String returnRejectReason,
        Instant returnedAt,
        BigDecimal refundAmount,
        // ---- Vòng đời đơn thuê (chỉ có giá trị khi orderType = RENTAL) ----
        Boolean contractSigned,
        Instant depositPaidAt,
        Instant deliveredAt,
        String deliveryConditionNote,
        Instant inspectedAt,
        String inspectionNote,
        BigDecimal damageAmount,
        String disputeReason,
        List<OrderItemDto> items,
        List<OrderAddonDto> addons
) {
}