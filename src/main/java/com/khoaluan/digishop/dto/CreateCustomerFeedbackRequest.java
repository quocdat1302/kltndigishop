package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin đăng 1 bài feedback (chọn ảnh khách + nội dung khách nói + gắn sản phẩm) — khách không tự gửi được nữa. */
public record CreateCustomerFeedbackRequest(
        @NotBlank(message = "Tên khách hàng không được để trống")
        String customerName,

        @NotBlank(message = "Nội dung feedback không được để trống")
        String comment,

        @NotNull(message = "Vui lòng chọn số sao đánh giá")
        @Min(value = 1, message = "Đánh giá tối thiểu 1 sao")
        @Max(value = 5, message = "Đánh giá tối đa 5 sao")
        Integer rating,

        /** Ảnh khách chụp bằng máy — admin tải lên hoặc dán link. */
        String imageUrl,

        /** Sản phẩm (máy ảnh) được feedback — hiện dạng "Chụp bằng [tên máy]". */
        Long productId
) {
}