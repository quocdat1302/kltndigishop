package com.khoaluan.digishop.entity;

public enum NotificationType {
    /** Cập nhật trạng thái đơn hàng/đơn thuê (tự động). */
    ORDER_UPDATE,
    /** Nhắc hạn trả thiết bị thuê (tự động, từ RentalReminderScheduler). */
    RENTAL_REMINDER,
    /** Có tin nhắn mới từ admin trong khung chat hỗ trợ. */
    CHAT_MESSAGE,
    /** Admin chủ động gửi thông báo/tin nhắn tới một khách hàng cụ thể. */
    ADMIN_MESSAGE,
    /** Cảnh báo admin về đơn thuê quá hạn trả (tự động, từ RentalReminderScheduler). */
    OVERDUE_RENTAL
}