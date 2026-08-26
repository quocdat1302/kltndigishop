package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.OtpPurpose;
import com.khoaluan.digishop.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByEmailIgnoreCaseAndPurposeAndUsedFalseOrderByIdDesc(String email, OtpPurpose purpose);
}
