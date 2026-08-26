package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.AuthProviderType;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProviderType authProvider, String providerId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    /** Dùng để gửi thông báo trong-app tới toàn bộ admin (đơn mới, hợp đồng vừa ký, thanh toán vừa nhận...). */
    java.util.List<User> findByRole(Role role);

    /** UC: GET /api/admin/users — danh sách người dùng có phân trang + lọc theo role/status/từ khoá. */
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR u.phone LIKE CONCAT('%', :keyword, '%'))
            ORDER BY u.createdAt DESC
            """)
    Page<User> search(
            @Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}