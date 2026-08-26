package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.CreateCustomerFeedbackRequest;
import com.khoaluan.digishop.dto.PublishFeedbackFromReviewRequest;
import com.khoaluan.digishop.entity.CustomerFeedback;
import com.khoaluan.digishop.entity.FeedbackLike;
import com.khoaluan.digishop.entity.FeedbackStatus;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.entity.ProductReview;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CustomerFeedbackRepository;
import com.khoaluan.digishop.repository.FeedbackLikeRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import com.khoaluan.digishop.repository.ProductReviewRepository;
import com.khoaluan.digishop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Feedback giờ do ADMIN đăng trực tiếp (chọn ảnh khách + nội dung + gắn sản phẩm) — khách hàng không tự
 * gửi feedback được nữa, chỉ được thả tim (xem FeedbackLike) cho các bài admin đã đăng.
 */
@Service
@RequiredArgsConstructor
public class CustomerFeedbackService {

    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final FeedbackLikeRepository feedbackLikeRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository productReviewRepository;
    private final UserRepository userRepository;

    /** Feedback công khai (đã duyệt/hiện) — hiển thị ở trang chủ/trang feedback cho mọi người xem. */
    @Transactional(readOnly = true)
    public List<CustomerFeedback> getTopFeedbacks() {
        return customerFeedbackRepository.findTop10ByStatusOrderByCreatedAtDesc(FeedbackStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<CustomerFeedback> getApprovedFeedbacksByProductId(Long productId) {
        return customerFeedbackRepository.findByProduct_IdAndStatusOrderByCreatedAtDesc(productId, FeedbackStatus.APPROVED);
    }

    /* ==================== Thả tim (khách) ==================== */

    @Transactional
    public void likeFeedback(User user, Long feedbackId) {
        if (feedbackLikeRepository.findByFeedback_IdAndUser_Id(feedbackId, user.getId()).isPresent()) {
            return; // đã thích rồi, bấm lại không lỗi, chỉ là no-op
        }
        CustomerFeedback feedback = customerFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", "Không tìm thấy feedback"));
        feedbackLikeRepository.save(FeedbackLike.builder().feedback(feedback).user(user).build());
    }

    @Transactional
    public void unlikeFeedback(User user, Long feedbackId) {
        feedbackLikeRepository.findByFeedback_IdAndUser_Id(feedbackId, user.getId())
                .ifPresent(feedbackLikeRepository::delete);
    }

    @Transactional(readOnly = true)
    public long countLikes(Long feedbackId) {
        return feedbackLikeRepository.countByFeedback_Id(feedbackId);
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedFeedbackIds(User user) {
        return feedbackLikeRepository.findLikedFeedbackIdsByUser(user.getId());
    }

    /* ==================== Admin — đăng/quản lý feedback ==================== */

    @Transactional(readOnly = true)
    public List<CustomerFeedback> getAllFeedbacksForAdmin() {
        return customerFeedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Admin đăng 1 bài feedback mới — hiện công khai ngay (APPROVED) vì admin đã tự kiểm duyệt trước khi đăng. */
    @Transactional
    public CustomerFeedback createFeedback(CreateCustomerFeedbackRequest req) {
        Product product = null;
        String productName = null;
        if (req.productId() != null) {
            product = productRepository.findById(req.productId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
            productName = product.getName();
        }
        CustomerFeedback feedback = CustomerFeedback.builder()
                .customerName(req.customerName())
                .comment(req.comment())
                .rating(req.rating())
                .imageUrl(req.imageUrl())
                .product(product)
                .productName(productName)
                .status(FeedbackStatus.APPROVED)
                .build();
        return customerFeedbackRepository.save(feedback);
    }

    /**
     * Đăng 1 đánh giá (ProductReview) khách đã gửi kèm ảnh lên trang Feedback công khai — dùng cho luồng:
     * khách đánh giá sản phẩm (mua hoặc thuê) kèm ảnh -> admin vào "Quản lý đánh giá" chọn ảnh ưng ý -> đăng lên đây.
     * Hiện công khai ngay (APPROVED) vì admin đã tự kiểm duyệt trước khi bấm đăng.
     */
    @Transactional
    public CustomerFeedback createFeedbackFromReview(Long reviewId, PublishFeedbackFromReviewRequest req) {
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Không tìm thấy đánh giá"));

        if (customerFeedbackRepository.existsBySourceReviewId(reviewId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PUBLISHED", "Đánh giá này đã được đăng lên Feedback rồi");
        }

        Product product = productRepository.findById(review.getProductId()).orElse(null);
        User user = userRepository.findById(review.getUserId()).orElse(null);

        String customerName = req != null && req.customerName() != null && !req.customerName().isBlank()
                ? req.customerName().trim() : review.getUserName();
        String comment = req != null && req.comment() != null && !req.comment().isBlank()
                ? req.comment().trim()
                : (review.getComment() != null && !review.getComment().isBlank()
                ? review.getComment() : "Khách hàng đã chia sẻ hình ảnh trải nghiệm sản phẩm.");
        Integer rating = req != null && req.rating() != null ? req.rating() : review.getRating();

        CustomerFeedback feedback = CustomerFeedback.builder()
                .customerName(customerName)
                .comment(comment)
                .rating(rating)
                .imageUrl(review.getImageUrl())
                .product(product)
                .productName(product != null ? product.getName() : null)
                .status(FeedbackStatus.APPROVED)
                .user(user)
                .sourceReviewId(reviewId)
                .build();

        return customerFeedbackRepository.save(feedback);
    }

    @Transactional
    public CustomerFeedback updateStatus(Long id, String rawStatus) {
        CustomerFeedback feedback = customerFeedbackRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", "Không tìm thấy feedback"));
        FeedbackStatus status;
        try {
            status = FeedbackStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Trạng thái không hợp lệ: " + rawStatus);
        }
        feedback.setStatus(status);
        return customerFeedbackRepository.save(feedback);
    }

    @Transactional
    public void deleteFeedback(Long id) {
        if (!customerFeedbackRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", "Không tìm thấy feedback");
        }
        // Xoá hết lượt thích trước để tránh lỗi khoá ngoại (feedback_likes -> customer_feedbacks)
        feedbackLikeRepository.deleteByFeedback_Id(id);
        customerFeedbackRepository.deleteById(id);
    }
}