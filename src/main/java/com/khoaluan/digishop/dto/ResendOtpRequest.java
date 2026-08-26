package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.OtpPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResendOtpRequest(
        @NotBlank @Email String email,
        @NotNull OtpPurpose purpose
) {
}
