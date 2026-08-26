package com.khoaluan.digishop.entity;

/** Trạng thái duyệt của 1 feedback khách gửi — chỉ feedback APPROVED mới hiện công khai trên trang chủ/trang feedback. */
public enum FeedbackStatus {
    PENDING,
    APPROVED,
    REJECTED
}