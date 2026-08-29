package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.LoyaltyInfoDto;
import com.khoaluan.digishop.dto.UpdateProfileRequest;
import com.khoaluan.digishop.dto.UpdateUserRoleRequest;
import com.khoaluan.digishop.dto.UserDto;
import com.khoaluan.digishop.dto.VerifyIdRequest;
import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.UserStatus;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CartItemRepository;
import com.khoaluan.digishop.repository.ChatMessageRepository;
import com.khoaluan.digishop.repository.FeedbackLikeRepository;
import com.khoaluan.digishop.repository.NotificationRepository;
import com.khoaluan.digishop.repository.OrderRepository;
import com.khoaluan.digishop.repository.ProductReviewRepository;
import com.khoaluan.digishop.repository.RentalContractRepository;
import com.khoaluan.digishop.repository.UserRepository;
import com.khoaluan.digishop.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final LoyaltyService loyaltyService;
    private final OrderRepository orderRepository;
    private final RentalContractRepository rentalContractRepository;
    private final CartItemRepository cartItemRepository;
    private final FeedbackLikeRepository feedbackLikeRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductReviewRepository productReviewRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;

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

    /**
     * Xoá cứng tài khoản + TOÀN BỘ dữ liệu liên quan (đơn hàng, giỏ hàng, đánh giá, wishlist,
     * thông báo, tin nhắn chat, hợp đồng thuê, lịch sử đăng nhập...). KHÔNG THỂ HOÀN TÁC.
     * Riêng feedback (customer_feedbacks) chỉ gỡ liên kết user (ON DELETE SET NULL ở DB) chứ
     * không xoá bài feedback, và refresh_tokens/refresh_tokens_old tự xoá theo (ON DELETE CASCADE).
     *
     * Thứ tự xoá bắt buộc phải theo đúng chiều phụ thuộc khoá ngoại:
     * rental_contracts -> orders (order_items/order_addons tự xoá theo qua JPA cascade khi xoá Order)
     * -> cart_items/feedback_likes/wishlist_items/product_reviews/notifications/chat_messages
     * -> login_histories (native, không có Entity) -> cuối cùng mới xoá User.
     */
    @Transactional
    public void deleteUserHard(Long targetUserId, Long actingAdminId) {
        if (targetUserId.equals(actingAdminId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_SELF",
                    "Không thể tự xoá tài khoản của chính mình — nhờ một Admin khác thực hiện việc này");
        }
        User target = getUserOrThrow(targetUserId);

        if (target.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_ADMIN",
                    "Không thể xoá Admin duy nhất còn lại trong hệ thống");
        }

        // 1) Hợp đồng thuê của các đơn hàng thuộc user này — phải xoá trước vì rental_contracts.order_id
        //    có FK RESTRICT tới orders, không tự cascade khi xoá Order ở tầng JPA.
        rentalContractRepository.deleteByOrder_User_Id(targetUserId);

        // 2) Đơn hàng — fetch entity (không dùng bulk delete) để JPA cascade tự xoá order_items/order_addons
        //    (cascade = CascadeType.ALL, orphanRemoval = true khai báo sẵn trên Order#items/#addons).
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(targetUserId);
        orderRepository.deleteAll(orders);

        // 3) Các bảng còn lại tham chiếu trực tiếp tới user, không có bảng con phụ thuộc thêm
        cartItemRepository.deleteByUserId(targetUserId);
        feedbackLikeRepository.deleteByUser_Id(targetUserId);
        wishlistItemRepository.deleteByUser_Id(targetUserId);
        productReviewRepository.deleteByUserId(targetUserId);
        notificationRepository.deleteByUserId(targetUserId);
        chatMessageRepository.deleteBySenderIdOrConversationUserId(targetUserId, targetUserId);
        userRepository.deleteLoginHistoriesByUserId(targetUserId);

        // 4) Cuối cùng xoá User — customer_feedbacks.user_id tự SET NULL, refresh_tokens(_old) tự CASCADE
        userRepository.delete(target);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }
}