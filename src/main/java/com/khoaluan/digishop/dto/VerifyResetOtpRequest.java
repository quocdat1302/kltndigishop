package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** UC: xac thuc ma OTP dat lai mat khau (buoc 1/2) - chua doi mat khau, chi kiem tra OTP dung. */
public record VerifyResetOtpRequest(
        @NotBlank @Email String email,
        @NotBlank String otp
) {
}