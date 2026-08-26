package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.NotificationDto;
import com.khoaluan.digishop.entity.Notification;
import com.khoaluan.digishop.entity.NotificationType;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.NotificationRepository;
import com.khoaluan.digishop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Thông báo trong-app. Mỗi thông báo vừa được lưu DB (để tải lại khi mở app) vừa đẩy realtime
 * qua STOMP tới /user/{userId}/queue/notifications (dùng chung hạ tầng WebSocket với chat UC-31).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NotificationDto create(Long userId, String title, String message, NotificationType type, Long relatedOrderId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .relatedOrderId(relatedOrderId)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        NotificationDto dto = toDto(notificationRepository.save(notification));

        try {
            // Dùng topic riêng theo userId thay vì convertAndSendToUser (đường /user/... cần Spring
            // định tuyến đúng theo Principal của phiên STOMP — thứ đang không ổn định trong môi trường
            // này). Topic "/topic/notifications.{userId}" không cần Principal, client tự biết userId
            // của chính mình (từ token đã đăng nhập) để subscribe đúng kênh.
            messagingTemplate.convertAndSend("/topic/notifications." + userId, dto);
        } catch (Exception e) {
            // Người dùng có thể đang offline (không có phiên STOMP nào) - không sao, họ vẫn thấy
            // thông báo này qua GET /api/notifications lúc mở lại app. Không được để lỗi đẩy realtime
            // làm hỏng luồng nghiệp vụ chính (vd cập nhật trạng thái đơn hàng) đang gọi hàm này.
            log.warn("Failed to push realtime notification to user {}: {}", userId, e.getMessage());
        }

        return dto;
    }

    /** Gửi 1 thông báo tới TẤT CẢ tài khoản admin — dùng cho các sự kiện admin cần biết ngay: đơn mới,
     *  khách vừa ký hợp đồng, vừa thanh toán, yêu cầu đổi trả... */
    @Transactional
    public void notifyAllAdmins(String title, String message, NotificationType type, Long relatedOrderId) {
        List<Long> adminIds = userRepository.findByRole(Role.ADMIN).stream().map(u -> u.getId()).toList();
        for (Long adminId : adminIds) {
            create(adminId, title, message, type, relatedOrderId);
        }
    }

    /** Admin chủ động gửi thông báo/tin nhắn tới một khách hàng cụ thể ("gửi tin nhắn"). */
    @Transactional
    public NotificationDto sendFromAdmin(Long targetUserId, String title, String message) {
        if (!userRepository.existsById(targetUserId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy khách hàng");
        }
        return create(targetUserId, title, message, NotificationType.ADMIN_MESSAGE, null);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo"));
        notification.setRead(true);
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getTitle(), n.getMessage(), n.getType().name(),
                n.getRelatedOrderId(), n.isRead(), n.getCreatedAt());
    }
}