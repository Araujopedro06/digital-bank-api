package com.pedro.bank.web.dto;

import com.pedro.bank.domain.PixCharge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What the receiving side gets back after asking to be paid.
 *
 * @param id      the only part that goes into the shared link; the client builds
 *                the URL from its own origin, so the API needs no idea where the
 *                front end is deployed
 * @param brCode  the standard "copia e cola" payload for the same request
 */
public record PixChargeResponse(UUID id, PixKeyResponse key, BigDecimal amount,
                                String description, Instant expiresAt, String brCode) {

    public static PixChargeResponse from(PixCharge charge, String brCode) {
        return new PixChargeResponse(
                charge.getId(),
                PixKeyResponse.from(charge.getKey()),
                charge.getAmount(),
                charge.getDescription(),
                charge.getExpiresAt(),
                brCode);
    }
}
