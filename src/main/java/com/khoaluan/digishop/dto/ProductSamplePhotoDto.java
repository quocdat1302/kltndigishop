package com.khoaluan.digishop.dto;

public record ProductSamplePhotoDto(
        Long id,
        Long productId,
        String imageUrl,
        String caption
) {
}