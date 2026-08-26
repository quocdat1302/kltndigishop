package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

/** signatureDataUrl: ảnh chữ ký vẽ tay dạng base64 data URL, xuất từ canvas ở trình duyệt. */
public record SignRentalContractRequest(
        @NotBlank(message = "Vui lòng ký tên trước khi hoàn tất") String signatureDataUrl
) {
}