package com.khoaluan.digishop.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDto(
        Long id,
        String name,
        String brand,
        String type,
        BigDecimal buyPrice,
        BigDecimal rentPrice,
        BigDecimal rentPriceWeekly,
        BigDecimal rentPriceMorning,
        BigDecimal rentPriceAfternoon,
        BigDecimal rentPriceEvening,
        String accessoriesIncluded,
        String techSpecs,
        String lensMount,
        String imageUrl,
        String description,
        Integer stockQuantity,
        Boolean isAvailable,
        String productCondition,
        Boolean isNew,
        Boolean isHot,
        Double averageRating,
        Long reviewCount,
        List<ProductSamplePhotoDto> samplePhotos,
        List<ProductAddonDto> addons
) {
}