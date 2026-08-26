package com.khoaluan.digishop.service;

import com.khoaluan.digishop.entity.Promotion;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public List<Promotion> getActivePromotions() {
        return promotionRepository.findActivePromotions(Instant.now());
    }

    public List<Promotion> getAllActivePromotions() {
        return promotionRepository.findByIsActiveTrueOrderByEndDateAsc();
    }

    /**
     * Kiểm tra mã khuyến mãi hợp lệ để áp dụng lúc checkout.
     * Ném lỗi nếu mã không tồn tại, không hoạt động, hoặc đã hết/chưa tới hạn.
     */
    public Promotion validateCode(String code) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_NOT_FOUND",
                        "Mã khuyến mãi \"" + code + "\" không tồn tại"));

        if (Boolean.FALSE.equals(promotion.getIsActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_INACTIVE", "Mã khuyến mãi đã bị vô hiệu hoá");
        }

        Instant now = Instant.now();
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_EXPIRED", "Mã khuyến mãi đã hết hạn hoặc chưa bắt đầu");
        }

        return promotion;
    }
}
