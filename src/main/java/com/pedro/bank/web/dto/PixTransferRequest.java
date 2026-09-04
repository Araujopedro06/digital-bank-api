package com.pedro.bank.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PixTransferRequest(
        @NotBlank @Size(max = 77) String key,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 120) String description,
        /** Required only when the sender has a face enrolled; obtained from POST /api/face/verify. */
        String faceToken) {
}
