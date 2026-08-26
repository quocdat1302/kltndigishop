package com.khoaluan.digishop.dto;

/**
 * Admin đăng 1 đánh giá (ProductReview) khách đã gửi lên trang Feedback công khai.
 * Các field đều tuỳ chọn — bỏ trống thì lấy nguyên nội dung khách đã viết trong đánh giá gốc,
 * admin chỉ cần chỉnh nếu muốn biên tập lại câu chữ trước khi đăng.
 */
public record PublishFeedbackFromReviewRequest(
        String customerName,
        String comment,
        Integer rating
) {
}