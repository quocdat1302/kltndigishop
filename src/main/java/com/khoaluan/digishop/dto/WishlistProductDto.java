package com.khoaluan.digishop.dto;

import java.math.BigDecimal;

/** Thông tin rút gọn 1 sản phẩm trong danh sách yêu thích — đủ để hiển thị thẻ sản phẩm. */
public record WishlistProductDto(
        Long id,
        String name,
        String brand,
        String type,
        BigDecimal buyPrice,
        BigDecimal rentPrice,
        String imageUrl,
        Boolean isAvailable
) {
}
