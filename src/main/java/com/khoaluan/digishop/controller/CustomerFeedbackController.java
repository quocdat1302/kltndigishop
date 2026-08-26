package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.CreateCustomerFeedbackRequest;
import com.khoaluan.digishop.dto.CustomerFeedbackDto;
import com.khoaluan.digishop.dto.PublishFeedbackFromReviewRequest;
import com.khoaluan.digishop.dto.UpdateFeedbackStatusRequest;
import com.khoaluan.digishop.entity.CustomerFeedback;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.CustomerFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Feedback ("kỷ niệm khách chia sẻ") — chỉ ADMIN đăng bài (chọn ảnh khách + gắn sản phẩm),
 * khách hàng chỉ được thả tim yêu thích, không tự đăng bài hay bình luận được.
 */
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class CustomerFeedbackController {

    private final CustomerFeedbackService customerFeedbackService;

    /** Feedback công khai (đã đăng) — hiển thị ở trang chủ / trang feedback cho mọi người xem. */
    @GetMapping
    public List<CustomerFeedbackDto> getTopFeedbacks(@AuthenticationPrincipal User user) {
        Set<Long> likedIds = user != null ? Set.copyOf(customerFeedbackService.getLikedFeedbackIds(user)) : Set.of();
        return customerFeedbackService.getTopFeedbacks().stream().map(f -> toDto(f, likedIds)).toList();
    }

    /** Feedback công khai của 1 sản phẩm cụ thể — dùng cho trang chi tiết sản phẩm. */
    @GetMapping("/products/{productId}")
    public List<CustomerFeedbackDto> getProductFeedbacks(@PathVariable Long productId, @AuthenticationPrincipal User user) {
        Set<Long> likedIds = user != null ? Set.copyOf(customerFeedbackService.getLikedFeedbackIds(user)) : Set.of();
        return customerFeedbackService.getApprovedFeedbacksByProductId(productId).stream().map(f -> toDto(f, likedIds)).toList();
    }

    /* ==================== Thả tim (khách đã đăng nhập) ==================== */

    @PostMapping("/{id}/like")
    public void likeFeedback(@AuthenticationPrincipal User user, @PathVariable Long id) {
        customerFeedbackService.likeFeedback(user, id);
    }

    @DeleteMapping("/{id}/like")
    public void unlikeFeedback(@AuthenticationPrincipal User user, @PathVariable Long id) {
        customerFeedbackService.unlikeFeedback(user, id);
    }

    /* ==================== Admin — đăng/quản lý feedback ==================== */

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerFeedbackDto> getAllFeedbacksForAdmin() {
        return customerFeedbackService.getAllFeedbacksForAdmin().stream().map(f -> toDto(f, Set.of())).toList();
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerFeedbackDto createFeedback(@Valid @RequestBody CreateCustomerFeedbackRequest req) {
        return toDto(customerFeedbackService.createFeedback(req), Set.of());
    }

    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerFeedbackDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateFeedbackStatusRequest req) {
        return toDto(customerFeedbackService.updateStatus(id, req.status()), Set.of());
    }

    /**
     * Đăng 1 đánh giá khách đã gửi (kèm ảnh, xem AdminProductReviewController) lên trang Feedback công khai.
     * Body có thể để trống {} nếu admin muốn giữ nguyên nội dung khách đã viết.
     */
    @PostMapping("/admin/from-review/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerFeedbackDto publishFromReview(
            @PathVariable Long reviewId,
            @RequestBody(required = false) PublishFeedbackFromReviewRequest req
    ) {
        return toDto(customerFeedbackService.createFeedbackFromReview(reviewId, req), Set.of());
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable Long id) {
        customerFeedbackService.deleteFeedback(id);
    }

    private CustomerFeedbackDto toDto(CustomerFeedback feedback, Set<Long> likedByMeIds) {
        return new CustomerFeedbackDto(
                feedback.getId(),
                feedback.getCustomerName(),
                feedback.getComment(),
                feedback.getRating(),
                feedback.getImageUrl(),
                feedback.getProduct() != null ? feedback.getProduct().getId() : null,
                feedback.getProductName(),
                feedback.getStatus() != null ? feedback.getStatus().name() : null,
                customerFeedbackService.countLikes(feedback.getId()),
                likedByMeIds.contains(feedback.getId()),
                feedback.getCreatedAt(),
                feedback.getSourceReviewId()
        );
    }
}