package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectReturnRequest(
        @NotBlank(message = "Vui lòng nhập lý do từ chối")
        String reason
) {
}
