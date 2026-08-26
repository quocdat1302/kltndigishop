package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Đặt mua hàng.
 * - Nếu cartItemIds null/rỗng: đặt mua toàn bộ sản phẩm (loại PURCHASE) đang có trong giỏ.
 * - Nếu cartItemIds có giá trị: chỉ đặt mua các dòng giỏ hàng được chọn (trang giỏ hàng cho tick chọn).
 * - buyNowProductId/buyNowQuantity: dùng cho nút "Mua ngay" ở trang sản phẩm, bỏ qua giỏ hàng.
 */
public record CheckoutPurchaseRequest(
        List<Long> cartItemIds,

        Long buyNowProductId,
        Integer buyNowQuantity,

        /** Mã khuyến mãi khách nhập lúc checkout (tuỳ chọn). */
        String promotionCode,

        @NotBlank(message = "Tên người nhận không được để trống")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        String recipientPhone,

        /** "PICKUP_AT_SHOP" hoặc "HOME_DELIVERY". Mặc định HOME_DELIVERY nếu bỏ trống (tương thích ngược). */
        String fulfillmentMethod,

        /** Bắt buộc nếu fulfillmentMethod = HOME_DELIVERY (hoặc pickupLocationId trỏ tới địa điểm giao tận nơi);
         *  có thể để trống nếu PICKUP_AT_SHOP. */
        String shippingAddress,

        /** id của PickupLocation khách chọn (địa điểm/hình thức nhận hàng) — ưu tiên hơn fulfillmentMethod
         *  nếu có; để trống thì hệ thống dùng lại fulfillmentMethod theo cách cũ (tương thích ngược). */
        Long pickupLocationId,

        String note
) {
}