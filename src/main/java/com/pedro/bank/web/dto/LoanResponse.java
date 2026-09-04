package com.pedro.bank.web.dto;

import com.pedro.bank.domain.Loan;
import com.pedro.bank.domain.LoanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param outstanding      the principal still owed, which is also what settling
 *                         early costs
 * @param remainingToTerm  what the rest of the instalments add up to, so the app
 *                         can show what settling early saves
 */
public record LoanResponse(UUID id, BigDecimal principal, BigDecimal monthlyRate,
                           int installments, BigDecimal installmentAmount, int paidInstallments,
                           BigDecimal outstanding, BigDecimal remainingToTerm, LoanStatus status,
                           Instant createdAt, Instant settledAt) {

    public static LoanResponse from(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getPrincipal(),
                loan.getMonthlyRate(),
                loan.getInstallments(),
                loan.getInstallmentAmount(),
                loan.getPaidInstallments(),
                loan.getOutstanding(),
                loan.remainingIfPaidToTerm(),
                loan.getStatus(),
                loan.getCreatedAt(),
                loan.getSettledAt());
    }
}
