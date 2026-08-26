package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.AdminProductReviewDto;
import com.khoaluan.digishop.dto.CreateReviewRequest;
import com.khoaluan.digishop.dto.ProductReviewDto;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.entity.ProductReview;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CustomerFeedbackRepository;
import com.khoaluan.digishop.repository.OrderItemRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import com.khoaluan.digishop.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;

    /** Trạng thái coi là "đã hoàn tất": đơn mua đã giao xong, đơn thuê đã trả máy xong (không tranh chấp/có tranh chấp đều tính vì khách đã thực sự dùng sản phẩm). */
    private static final List<OrderStatus> COMPLETED_STATUSES =
            List.of(OrderStatus.COMPLETED, OrderStatus.DISPUTED);

    @Transactional
    public ProductReviewDto createReview(Long productId, User user, CreateReviewRequest req) {
        if (!productRepository.existsById(productId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
        }

        if (productReviewRepository.existsByProductIdAndUserId(productId, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "Bạn đã đánh giá sản phẩm này rồi");
        }

        /** Chỉ khách hàng đã thực sự mua hoặc thuê xong sản phẩm này mới được đánh giá ("verified purchase"),
         *  áp dụng cho cả đơn mua (PURCHASE) lẫn đơn thuê (RENTAL) vì cùng dùng chung bảng order_items. */
        boolean verifiedPurchase = orderItemRepository
                .existsByProduct_IdAndOrder_User_IdAndOrder_StatusIn(productId, user.getId(), COMPLETED_STATUSES);
        if (!verifiedPurchase) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_PURCHASED",
                    "Bạn cần mua hoặc thuê xong sản phẩm này thì mới đánh giá được");
        }

        ProductReview review = ProductReview.builder()
                .productId(productId)
                .userId(user.getId())
                .userName(user.getName())
                .rating(req.rating())
                .comment(req.comment())
                .imageUrl(req.imageUrl())
                .createdAt(Instant.now())
                .build();

        return toDto(productReviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ProductReviewDto> getReviews(Long productId) {
        return productReviewRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream().map(this::toDto).toList();
    }

    /** Dùng bởi ProductController để map điểm TB/số lượng review vào ProductDto mà không bị N+1 query. */
    @Transactional(readOnly = true)
    public Map<Long, ProductReviewRepository.RatingSummary> getAllRatingSummaries() {
        return productReviewRepository.getAllRatingSummaries().stream()
                .collect(java.util.stream.Collectors.toMap(ProductReviewRepository.RatingSummary::getProductId, s -> s));
    }

    /**
     * Danh sách TẤT CẢ đánh giá (cả sản phẩm mua lẫn thuê) cho trang admin "Quản lý đánh giá" —
     * admin xem ảnh khách gửi kèm đánh giá, từ đây chọn ảnh ưng ý để đăng lên trang Feedback công khai.
     */
    @Transactional(readOnly = true)
    public List<AdminProductReviewDto> getAllReviewsForAdmin() {
        List<ProductReview> reviews = productReviewRepository.findAllByOrderByCreatedAtDesc();

        Map<Long, Product> productsById = productRepository
                .findAllById(reviews.stream().map(ProductReview::getProductId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        Set<Long> publishedReviewIds = Set.copyOf(customerFeedbackRepository.findAllSourceReviewIds());

        return reviews.stream().map(r -> {
            Product p = productsById.get(r.getProductId());
            return new AdminProductReviewDto(
                    r.getId(),
                    r.getProductId(),
                    p != null ? p.getName() : null,
                    p != null ? p.getImageUrl() : null,
                    r.getUserId(),
                    r.getUserName(),
                    r.getRating(),
                    r.getComment(),
                    r.getImageUrl(),
                    r.getCreatedAt(),
                    publishedReviewIds.contains(r.getId())
            );
        }).toList();
    }

    /** Admin xoá 1 đánh giá không phù hợp (spam, ngôn từ xấu...). */
    @Transactional
    public void deleteReview(Long id) {
        if (!productReviewRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Không tìm thấy đánh giá");
        }
        productReviewRepository.deleteById(id);
    }

    private ProductReviewDto toDto(ProductReview r) {
        return new ProductReviewDto(
                r.getId(),
                r.getProductId(),
                r.getUserId(),
                r.getUserName(),
                r.getRating(),
                r.getComment(),
                r.getImageUrl(),
                r.getCreatedAt()
        );
    }
}