package com.khoaluan.digishop.dto;

import java.time.Instant;

public record RentalContractDto(
        Long id,
        Long orderId,
        String contractText,
        String signatureDataUrl,
        Instant signedAt
) {
}