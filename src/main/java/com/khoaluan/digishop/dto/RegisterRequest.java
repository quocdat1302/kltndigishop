package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Mật khẩu cần chữ hoa, số và ký tự đặc biệt"
        )
        String password,
        @NotBlank String confirmPassword,
        @AssertTrue(message = "Bạn cần đồng ý điều khoản sử dụng") boolean acceptTerms
) {
}
