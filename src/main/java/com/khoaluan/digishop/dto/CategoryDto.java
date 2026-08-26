package com.khoaluan.digishop.dto;

public record CategoryDto(
        Long id,
        String name,
        String type,
        Integer productCount,
        String imageUrl
) {
}