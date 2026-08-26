package com.khoaluan.digishop.dto;

import com.khoaluan.digishop.entity.AuthProviderType;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.UserStatus;
import lombok.Builder;

import java.time.Instant;

@Builder
public record UserDto(
        Long id,
        String name,
        String email,
        String phone,
        String avatarUrl,
        UserStatus status,
        AuthProviderType authProvider,
        Role role,
        boolean identityVerified,
        String idCardNumber,
        String idCardFrontUrl,
        String idCardBackUrl,
        Instant idCardSubmittedAt,
        Instant createdAt,
        LoyaltyInfoDto loyalty
) {
    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .authProvider(user.getAuthProvider())
                .role(user.getRole())
                .identityVerified(user.isIdentityVerified())
                .idCardNumber(user.getIdCardNumber())
                .idCardFrontUrl(user.getIdCardFrontUrl())
                .idCardBackUrl(user.getIdCardBackUrl())
                .idCardSubmittedAt(user.getIdCardSubmittedAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserDto from(User user, LoyaltyInfoDto loyalty) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .authProvider(user.getAuthProvider())
                .role(user.getRole())
                .identityVerified(user.isIdentityVerified())
                .idCardNumber(user.getIdCardNumber())
                .idCardFrontUrl(user.getIdCardFrontUrl())
                .idCardBackUrl(user.getIdCardBackUrl())
                .idCardSubmittedAt(user.getIdCardSubmittedAt())
                .createdAt(user.getCreatedAt())
                .loyalty(loyalty)
                .build();
    }
}