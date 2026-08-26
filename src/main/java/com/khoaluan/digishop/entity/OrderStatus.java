package com.khoaluan.digishop.entity;

public enum OrderStatus {
    /** Vừa tạo, chờ xác nhận. */
    PENDING,
    /** Đã xác nhận. Đơn mua: đang chuẩn bị hàng. Đơn thuê: chờ khách đóng cọc. */
    CONFIRMED,
    /** Đang giao hàng (chỉ áp dụng đơn mua — đơn thuê dùng DEPOSIT_PAID/DELIVERED thay cho bước này). */
    DELIVERING,
    /** Mua: đã giao xong (trạng thái cuối). Thuê: dùng cho đơn không phát sinh tranh chấp (trạng thái cuối, xem inspectRentalReturn). */
    COMPLETED,
    /** Bị huỷ (bởi khách hoặc admin). */
    CANCELLED,
    /** Khách yêu cầu đổi trả, đang chờ admin/staff duyệt (chỉ áp dụng đơn mua). */
    RETURN_REQUESTED,
    /** Khách yêu cầu trả máy trong đơn thuê, chờ admin xác nhận. */
    RENTAL_RETURN_REQUESTED,
    /** Yêu cầu đổi trả đã được duyệt, đã hoàn tiền (chỉ áp dụng đơn mua). */
    RETURNED,

    // ---------------------------------------------------------------
    // Các bước riêng của quy trình thuê (chỉ áp dụng đơn RENTAL).
    // Luồng: PENDING -> CONFIRMED -> DEPOSIT_PAID -> DELIVERED
    //        -> RENTAL_RETURNED -> (kiểm tra) -> COMPLETED | DISPUTED
    // ---------------------------------------------------------------

    /** Khách đã đóng tiền cọc. */
    DEPOSIT_PAID,
    /** Đã giao thiết bị cho khách, đã ghi nhận tình trạng máy lúc giao. Đây cũng là trạng thái trong suốt thời gian thuê. */
    DELIVERED,
    /** Khách đã trả thiết bị, đang chờ nhân viên kho kiểm tra tình trạng. */
    RENTAL_RETURNED,
    /** Đã kiểm tra xong tình trạng máy lúc nhận lại — chờ admin chọn "Hoàn cọc" hoặc "Trừ cọc". */
    INSPECTED,
    /** Phát sinh hư hỏng/trễ hạn lúc kiểm tra, đã trừ một phần cọc (trạng thái cuối). */
    DISPUTED
}