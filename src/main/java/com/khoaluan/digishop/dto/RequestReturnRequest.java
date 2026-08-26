package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RequestReturnRequest(
        @NotBlank(message = "Vui lòng nhập lý do đổi trả")
        String reason,

        /** Ảnh bằng chứng (hư hỏng/trầy xước...), tuỳ chọn nhưng nên có để admin xét duyệt nhanh hơn. */
        List<String> imageUrls
) {
}