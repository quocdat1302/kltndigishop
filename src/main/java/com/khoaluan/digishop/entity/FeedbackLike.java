package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 1 lượt khách thả tim yêu thích 1 bài feedback — khách chỉ được thả tim, không được viết bình luận riêng. */
@Entity
@Table(name = "feedback_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"feedback_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private CustomerFeedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}