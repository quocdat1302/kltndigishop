package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.AdminProductReviewDto;
import com.khoaluan.digishop.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Trang admin "Quản lý đánh giá" — xem tất cả đánh giá khách gửi (kèm ảnh) cho cả sản phẩm mua
 * lẫn sản phẩm thuê, để từ đó chọn ảnh đăng lên trang Feedback công khai (xem CustomerFeedbackController).
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductReviewController {

    private final ProductReviewService productReviewService;

    @GetMapping
    public List<AdminProductReviewDto> getAllReviews() {
        return productReviewService.getAllReviewsForAdmin();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable Long id) {
        productReviewService.deleteReview(id);
    }
}