package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.CreateReviewRequest;
import com.khoaluan.digishop.dto.ProductReviewDto;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    @GetMapping
    public List<ProductReviewDto> getReviews(@PathVariable Long productId) {
        return productReviewService.getReviews(productId);
    }

    /** Chỉ khách hàng đã mua/thuê xong sản phẩm này mới được đánh giá - xem ProductReviewService. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewDto createReview(
            @PathVariable Long productId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateReviewRequest req
    ) {
        return productReviewService.createReview(productId, user, req);
    }
}