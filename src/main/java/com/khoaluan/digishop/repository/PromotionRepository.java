package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("SELECT p FROM Promotion p WHERE p.isActive = true AND p.startDate <= :now AND p.endDate >= :now")
    List<Promotion> findActivePromotions(Instant now);

    List<Promotion> findByIsActiveTrueOrderByEndDateAsc();

    Optional<Promotion> findByCodeIgnoreCase(String code);
}