package com.pedro.bank.web.dto;

import com.pedro.bank.service.LoanService;

import java.math.BigDecimal;
import java.util.List;

public record LoanTermsResponse(BigDecimal maxPrincipal, BigDecimal monthlyRate,
                                List<Integer> allowedInstallments) {

    public static LoanTermsResponse from(LoanService.Terms terms) {
        return new LoanTermsResponse(terms.maxPrincipal(), terms.monthlyRate(),
                terms.allowedInstallments());
    }
}
