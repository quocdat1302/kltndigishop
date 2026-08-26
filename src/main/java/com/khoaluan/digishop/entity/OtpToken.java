package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "otp_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastSentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean used;
}
