package com.pedro.bank.web.dto;

import java.math.BigDecimal;

/**
 * @param payload the "copia e cola" string, which is also exactly what the QR
 *                code encodes — the picture is only a way of typing it
 */
public record BrCodeResponse(String payload, PixKeyResponse key, BigDecimal amount,
                             String description) {
}
