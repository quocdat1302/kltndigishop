package com.khoaluan.digishop.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderProductsRequest(
        @NotEmpty(message = "Danh sách sản phẩm sắp xếp không được rỗng")
        List<Long> orderedIds
) {
}

