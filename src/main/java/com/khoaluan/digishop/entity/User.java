package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    /** Null for users that only ever signed in via a social provider. */
    @Column(name = "password_hash")
    private String passwordHash;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "auth_provider")
    private AuthProviderType authProvider;

    /** Google/Facebook user id, null for LOCAL accounts. */
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean identityVerified;

    /** Số CCCD/CMND đã nộp để xác thực (bắt buộc để thuê thiết bị). */
    @Column(name = "id_card_number")
    private String idCardNumber;

    /** URL ảnh mặt trước/sau CCCD — theo đúng convention imageUrl sẵn có của Product (chỉ lưu URL,
     *  không có pipeline upload file thật; FE tự upload lên nơi lưu trữ ảnh rồi gửi URL lên đây). */
    @Column(name = "id_card_front_url")
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url")
    private String idCardBackUrl;

    @Column(name = "id_card_submitted_at")
    private Instant idCardSubmittedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (role == null) {
            role = Role.CUSTOMER;
        }
    }
}