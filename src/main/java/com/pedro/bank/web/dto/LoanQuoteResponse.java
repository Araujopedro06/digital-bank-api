package com.pedro.bank.web.dto;

import com.pedro.bank.service.LoanService;

import java.math.BigDecimal;

public record LoanQuoteResponse(BigDecimal principal, int installments, BigDecimal monthlyRate,
                                BigDecimal installmentAmount, BigDecimal total,
                                BigDecimal totalInterest) {

    public static LoanQuoteResponse from(LoanService.Quote quote) {
        return new LoanQuoteResponse(quote.principal(), quote.installments(), quote.monthlyRate(),
                quote.installmentAmount(), quote.total(), quote.totalInterest());
    }
}
