package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.AdminSendNotificationRequest;
import com.khoaluan.digishop.dto.NotificationDto;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/api/notifications")
    public List<NotificationDto> getMyNotifications(@AuthenticationPrincipal User user) {
        return notificationService.getMyNotifications(user.getId());
    }

    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", notificationService.getUnreadCount(user.getId()));
    }

    @PutMapping("/api/notifications/{id}/read")
    public NotificationDto markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return notificationService.markRead(user.getId(), id);
    }

    @PutMapping("/api/notifications/read-all")
    public void markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
    }

    /** Admin chủ động gửi thông báo/tin nhắn tới một khách hàng cụ thể. */
    @PostMapping("/api/admin/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto sendFromAdmin(@Valid @RequestBody AdminSendNotificationRequest req) {
        return notificationService.sendFromAdmin(req.targetUserId(), req.title(), req.message());
    }
}