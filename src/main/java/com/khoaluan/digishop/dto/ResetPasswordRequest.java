package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** UC: dat mat khau moi (buoc 2/2) - dung resetToken cap o buoc xac thuc OTP, khong can gui lai OTP/email. */
public record ResetPasswordRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Mật khẩu cần chữ hoa, số và ký tự đặc biệt"
        )
        String newPassword
) {
}