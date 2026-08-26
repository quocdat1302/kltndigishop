package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.PromotionDto;
import com.khoaluan.digishop.entity.Promotion;
import com.khoaluan.digishop.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping("/active")
    public List<PromotionDto> getActivePromotions() {
        return promotionService.getActivePromotions().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping
    public List<PromotionDto> getAllActivePromotions() {
        return promotionService.getAllActivePromotions().stream()
                .map(this::toDto)
                .toList();
    }

    private PromotionDto toDto(Promotion promotion) {
        return new PromotionDto(
                promotion.getId(),
                promotion.getTitle(),
                promotion.getDescription(),
                promotion.getCode(),
                promotion.getDiscountPercent(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getIsActive()
        );
    }
}
