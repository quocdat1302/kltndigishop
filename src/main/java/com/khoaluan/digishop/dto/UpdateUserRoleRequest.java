package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Vui lòng chọn vai trò")
        Role role
) {
}