package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);

    /** Dùng cho trang admin "Quản lý đánh giá" — xem tất cả đánh giá (cả sản phẩm mua lẫn thuê) để chọn ảnh đăng feedback. */
    List<ProductReview> findAllByOrderByCreatedAtDesc();

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    /** Xoá toàn bộ đánh giá của 1 user — dùng khi admin xoá cứng tài khoản. */
    void deleteByUserId(Long userId);

    /** Lấy điểm TB + số lượng đánh giá cho TẤT CẢ sản phẩm trong 1 query, tránh N+1 khi map danh sách sản phẩm. */
    @Query("""
            SELECT r.productId AS productId, AVG(r.rating) AS avgRating, COUNT(r) AS reviewCount
            FROM ProductReview r
            GROUP BY r.productId
            """)
    List<RatingSummary> getAllRatingSummaries();

    interface RatingSummary {
        Long getProductId();
        Double getAvgRating();
        Long getReviewCount();
    }
}