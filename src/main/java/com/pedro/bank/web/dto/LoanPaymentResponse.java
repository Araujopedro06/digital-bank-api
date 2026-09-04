package com.pedro.bank.web.dto;

import com.pedro.bank.domain.Loan;

import java.math.BigDecimal;

/**
 * What one payment was made of. The split is worth showing: on a Price table the
 * first instalments are mostly interest, and seeing that is the whole education.
 */
public record LoanPaymentResponse(BigDecimal total, BigDecimal interest, BigDecimal amortized,
                                  boolean settled, LoanResponse loan) {

    public static LoanPaymentResponse from(Loan.Payment payment, Loan loan) {
        return new LoanPaymentResponse(payment.total(), payment.interest(), payment.amortized(),
                payment.settled(), LoanResponse.from(loan));
    }
}
