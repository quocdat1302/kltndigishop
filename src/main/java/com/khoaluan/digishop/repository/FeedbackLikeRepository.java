package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.FeedbackLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackLikeRepository extends JpaRepository<FeedbackLike, Long> {
    Optional<FeedbackLike> findByFeedback_IdAndUser_Id(Long feedbackId, Long userId);

    long countByFeedback_Id(Long feedbackId);

    /** Xoá hết các lượt thích của 1 feedback — gọi trước khi xoá feedback để tránh lỗi khoá ngoại. */
    void deleteByFeedback_Id(Long feedbackId);

    /** Danh sách id feedback mà user này đã thích — dùng để tô đỏ tim ở trang feedback. */
    @Query("select fl.feedback.id from FeedbackLike fl where fl.user.id = :userId")
    List<Long> findLikedFeedbackIdsByUser(@Param("userId") Long userId);
}