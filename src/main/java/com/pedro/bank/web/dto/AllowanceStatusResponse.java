package com.pedro.bank.web.dto;

import com.pedro.bank.service.AllowanceService;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param availableAt when she will pick up the phone again, or null if she will
 *                    now
 */
public record AllowanceStatusResponse(Instant availableAt, BigDecimal generousLimit,
                                      BigDecimal haggleLimit) {

    public static AllowanceStatusResponse from(AllowanceService.Status status) {
        return new AllowanceStatusResponse(
                status.availableAt(), status.generousLimit(), status.haggleLimit());
    }
}
