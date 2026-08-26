package com.khoaluan.digishop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private static final long RESET_PASSWORD_TOKEN_TTL_MS = 10 * 60 * 1000; // 10 phut
    private static final String RESET_PASSWORD_PURPOSE = "RESET_PASSWORD";

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessTokenExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public String generateAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /** Sinh token tam (10 phut) sau khi OTP dat lai mat khau da duoc xac thuc thanh cong. */
    public String generateResetPasswordToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + RESET_PASSWORD_TOKEN_TTL_MS);
        return Jwts.builder()
                .subject(email)
                .claim("purpose", RESET_PASSWORD_PURPOSE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** Giai ma resetToken, ném JwtException neu het han/sai chu ky/sai muc dich. */
    public String extractResetPasswordEmail(String resetToken) {
        Claims claims = parseClaims(resetToken);
        if (!RESET_PASSWORD_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("Token không đúng mục đích đặt lại mật khẩu");
        }
        return claims.getSubject();
    }
}