package com.pedro.bank.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** A link opened: who it pays, and what it asks for. */
public record PixChargeLookupResponse(PixRecipientResponse recipient, BigDecimal amount,
                                      String description, Instant expiresAt) {
}
