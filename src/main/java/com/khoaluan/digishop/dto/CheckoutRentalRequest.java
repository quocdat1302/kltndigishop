package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * Đặt thuê thiết bị.
 * - Nếu cartItemIds null/rỗng: đặt thuê toàn bộ sản phẩm (loại RENTAL) đang có trong giỏ,
 *   dùng đúng ngày thuê đã lưu sẵn trên từng dòng giỏ hàng.
 * - Nếu cartItemIds có giá trị: chỉ đặt thuê các dòng giỏ hàng được chọn.
 * - rentNowProductId/rentNowQuantity/rentNowStartDate/rentNowEndDate: dùng cho nút
 *   "Thuê ngay" ở trang sản phẩm, bỏ qua giỏ hàng.
 */
public record CheckoutRentalRequest(
        List<Long> cartItemIds,

        Long rentNowProductId,
        Integer rentNowQuantity,
        @FutureOrPresent(message = "Ngày bắt đầu thuê không được ở quá khứ")
        LocalDate rentNowStartDate,
        @FutureOrPresent(message = "Ngày kết thúc thuê không được ở quá khứ")
        LocalDate rentNowEndDate,

        /**
         * Thuê theo buổi trong ngày thay vì theo ngày: "MORNING" | "AFTERNOON" | "EVENING".
         * Khi có giá trị, bỏ qua rentPrice/ngày và dùng giá theo buổi tương ứng của sản phẩm;
         * rentNowStartDate phải bằng rentNowEndDate (thuê đúng 1 ngày, 1 buổi).
         */
        String rentNowSlot,

        /** Mã khuyến mãi khách nhập lúc checkout (tuỳ chọn). */
        String promotionCode,

        @NotBlank(message = "Tên người nhận không được để trống")
        String recipientName,

        @NotBlank(message = "Số điện thoại không được để trống")
        String recipientPhone,

        /** "PICKUP_AT_SHOP" hoặc "HOME_DELIVERY". Mặc định HOME_DELIVERY nếu bỏ trống (tương thích ngược). */
        String fulfillmentMethod,

        /** Bắt buộc nếu fulfillmentMethod = HOME_DELIVERY; có thể để trống nếu PICKUP_AT_SHOP. */
        String shippingAddress,

        /** id của PickupLocation khách chọn (địa điểm/hình thức nhận máy) — ưu tiên hơn fulfillmentMethod
         *  nếu có; để trống thì hệ thống dùng lại fulfillmentMethod theo cách cũ (tương thích ngược). */
        Long pickupLocationId,

        /**
         * Danh sách id ProductAddon (phụ kiện trả thêm) khách chọn thêm — chỉ áp dụng cho rentNowProductId
         * (nút "Thuê ngay"). Phụ kiện included=true của sản phẩm luôn tự động thêm miễn phí, không cần liệt kê ở đây.
         */
        List<Long> selectedAddonIds,

        String note
) {
}