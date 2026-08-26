package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.UpdateProfileRequest;
import com.khoaluan.digishop.dto.UpdateUserRoleRequest;
import com.khoaluan.digishop.dto.UserDto;
import com.khoaluan.digishop.dto.VerifyIdRequest;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.UserStatus;
import com.khoaluan.digishop.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ------------------------------------------------------------------
    // Hồ sơ cá nhân (mọi user đã đăng nhập)
    // ------------------------------------------------------------------

    @GetMapping("/api/users/me")
    public UserDto getMyProfile(@AuthenticationPrincipal User user) {
        return userService.getProfile(user.getId());
    }

    @PutMapping("/api/users/me")
    public UserDto updateMyProfile(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(user.getId(), req);
    }

    /** Bắt buộc trước khi thuê thiết bị — xem gate ở OrderService#checkoutRental. */
    @PostMapping("/api/users/me/verify-id")
    public UserDto verifyMyId(@AuthenticationPrincipal User user, @Valid @RequestBody VerifyIdRequest req) {
        return userService.verifyId(user.getId(), req);
    }

    // ------------------------------------------------------------------
    // Quản lý người dùng (Admin)
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserDto> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userService.searchUsers(role, status, keyword, PageRequest.of(page, size));
    }

    @PutMapping("/api/admin/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateUserRole(
            @PathVariable Long id,
            @AuthenticationPrincipal User actingAdmin,
            @Valid @RequestBody UpdateUserRoleRequest req
    ) {
        return userService.updateRole(id, actingAdmin.getId(), req);
    }
}