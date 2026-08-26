package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.CustomerFeedback;
import com.khoaluan.digishop.entity.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {

    List<CustomerFeedback> findTop10ByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<CustomerFeedback> findByProduct_IdAndStatusOrderByCreatedAtDesc(Long productId, FeedbackStatus status);

    List<CustomerFeedback> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<CustomerFeedback> findAllByOrderByCreatedAtDesc();

    boolean existsBySourceReviewId(Long sourceReviewId);

    /** Dùng để đánh dấu trên trang "Quản lý đánh giá" đánh giá nào đã được đăng lên Feedback rồi. */
    @Query("SELECT f.sourceReviewId FROM CustomerFeedback f WHERE f.sourceReviewId IS NOT NULL")
    List<Long> findAllSourceReviewIds();
}