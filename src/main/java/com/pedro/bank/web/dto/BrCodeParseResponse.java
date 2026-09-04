package com.pedro.bank.web.dto;

import java.math.BigDecimal;

/** A code read back: who it pays, and what it suggests paying. */
public record BrCodeParseResponse(PixRecipientResponse recipient, BigDecimal amount,
                                  String description) {
}
