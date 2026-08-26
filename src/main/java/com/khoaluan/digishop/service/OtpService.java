package com.khoaluan.digishop.service;

import com.khoaluan.digishop.entity.OtpPurpose;
import com.khoaluan.digishop.entity.OtpToken;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final OtpTokenRepository otpTokenRepository;
    private final EmailService emailService;

    public record IssuedOtp(String code, long expiresInSeconds) {
    }

    @Transactional
    public IssuedOtp issue(String email, OtpPurpose purpose) {
        otpTokenRepository.findTopByEmailIgnoreCaseAndPurposeAndUsedFalseOrderByIdDesc(email, purpose)
                .ifPresent(existing -> {
                    Instant cooldownEnds = existing.getLastSentAt().plus(RESEND_COOLDOWN);
                    if (Instant.now().isBefore(cooldownEnds)) {
                        long remaining = Duration.between(Instant.now(), cooldownEnds).getSeconds();
                        Map<String, Object> details = new HashMap<>();
                        details.put("cooldownSeconds", Math.max(remaining, 1));
                        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                                "Vui lòng đợi trước khi gửi lại mã OTP.", details);
                    }
                    existing.setUsed(true);
                    otpTokenRepository.save(existing);
                });

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        OtpToken token = OtpToken.builder()
                .email(email.toLowerCase())
                .code(code)
                .purpose(purpose)
                .expiresAt(now.plus(OTP_TTL))
                .lastSentAt(now)
                .attempts(0)
                .used(false)
                .build();
        otpTokenRepository.save(token);

        emailService.sendOtpEmail(email, code, purpose.name());
        return new IssuedOtp(code, OTP_TTL.getSeconds());
    }

    @Transactional
    public void verify(String email, String code, OtpPurpose purpose) {
        OtpToken token = otpTokenRepository
                .findTopByEmailIgnoreCaseAndPurposeAndUsedFalseOrderByIdDesc(email, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_NOT_FOUND",
                        "Không tìm thấy mã OTP, vui lòng yêu cầu gửi lại."));

        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "Mã OTP đã hết hạn.");
        }
        if (token.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
                    "Bạn đã nhập sai quá số lần cho phép, vui lòng yêu cầu mã mới.");
        }
        if (!token.getCode().equals(code)) {
            token.setAttempts(token.getAttempts() + 1);
            otpTokenRepository.save(token);
            Map<String, Object> details = new HashMap<>();
            details.put("attemptsRemaining", Math.max(MAX_ATTEMPTS - token.getAttempts(), 0));
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Mã OTP không đúng.", details);
        }

        token.setUsed(true);
        otpTokenRepository.save(token);
    }
}
