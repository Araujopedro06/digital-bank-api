package com.pedro.bank.web.dto;

import com.pedro.bank.service.AllowanceService;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The outcome as a value, never as a sentence: what the aunt actually says is
 * copy, and copy is the client's job — which also lets it pick a different line
 * each time instead of repeating one.
 */
public record AllowanceResponse(AllowanceService.Outcome outcome, BigDecimal asked,
                                BigDecimal granted, BigDecimal balance, Instant availableAt) {

    public static AllowanceResponse from(AllowanceService.Result result) {
        return new AllowanceResponse(result.outcome(), result.asked(), result.granted(),
                result.balance(), result.availableAt());
    }
}
