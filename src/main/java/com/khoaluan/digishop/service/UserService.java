package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.LoyaltyInfoDto;
import com.khoaluan.digishop.dto.UpdateProfileRequest;
import com.khoaluan.digishop.dto.UpdateUserRoleRequest;
import com.khoaluan.digishop.dto.UserDto;
import com.khoaluan.digishop.dto.VerifyIdRequest;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.UserStatus;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final LoyaltyService loyaltyService;

    @Transactional(readOnly = true)
    public UserDto getProfile(Long userId) {
        User user = getUserOrThrow(userId);
        return UserDto.from(user, loyaltyService.getLoyaltyInfo(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest req) {
        User user = getUserOrThrow(userId);

        if (req.phone() != null && !req.phone().isBlank() && !req.phone().equals(user.getPhone())
                && userRepository.existsByPhone(req.phone())) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "Số điện thoại này đã được dùng bởi tài khoản khác");
        }

        user.setName(req.name());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.avatarUrl() != null) user.setAvatarUrl(req.avatarUrl());

        return UserDto.from(userRepository.save(user));
    }

    /** UC: POST /api/users/me/verify-id — nộp CCCD/CMND, bắt buộc trước khi thuê thiết bị. */
    @Transactional
    public UserDto verifyId(Long userId, VerifyIdRequest req) {
        User user = getUserOrThrow(userId);

        // MVP tự-khai-báo: không có bước duyệt thủ công của nhân viên, xác thực có hiệu lực ngay.
        // Muốn chặt chẽ hơn (chống gian lận) thì nên chuyển thành "chờ duyệt" + thêm 1 API admin duyệt riêng.
        user.setIdCardNumber(req.idCardNumber());
        user.setIdCardFrontUrl(req.idCardFrontUrl());
        user.setIdCardBackUrl(req.idCardBackUrl());
        user.setIdCardSubmittedAt(Instant.now());
        user.setIdentityVerified(true);

        return UserDto.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserDto> searchUsers(Role role, UserStatus status, String keyword, Pageable pageable) {
        return userRepository.search(role, status, keyword, pageable)
                .map(u -> UserDto.from(u, loyaltyService.getLoyaltyInfo(u.getId())));
    }

    @Transactional
    public UserDto updateRole(Long targetUserId, Long actingAdminId, UpdateUserRoleRequest req) {
        if (targetUserId.equals(actingAdminId) && req.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DEMOTE_SELF",
                    "Không thể tự hạ quyền của chính mình — nhờ một Admin khác thực hiện việc này");
        }
        User target = getUserOrThrow(targetUserId);
        target.setRole(req.role());
        User saved = userRepository.save(target);
        return UserDto.from(saved, loyaltyService.getLoyaltyInfo(saved.getId()));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
}