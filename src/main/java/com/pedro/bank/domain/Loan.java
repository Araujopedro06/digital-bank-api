package com.pedro.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "monthly_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal monthlyRate;

    @Column(nullable = false)
    private int installments;

    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "paid_installments", nullable = false)
    private int paidInstallments;

    /**
     * The principal still owed — not the sum of the instalments left. Interest
     * that has not been charged yet is not a debt, and treating it as one is what
     * would make settling early pointless.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstanding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;

    protected Loan() {
    }

    public Loan(Account account, BigDecimal principal, BigDecimal monthlyRate, int installments) {
        this.account = account;
        this.principal = principal;
        this.monthlyRate = monthlyRate;
        this.installments = installments;
        this.installmentAmount = Amortization.installment(principal, monthlyRate, installments);
        this.outstanding = principal;
        this.status = LoanStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    /** What one instalment is made of, so the app can show the split. */
    public record Payment(BigDecimal total, BigDecimal interest, BigDecimal amortized,
                          BigDecimal outstandingAfter, boolean settled) {
    }

    /**
     * Charges one instalment.
     *
     * <p>The last one is whatever is actually left plus its interest, rather than
     * the quoted figure. Rounding each instalment to centavos leaves a few cents
     * of drift over two years, and a borrower who has paid every instalment must
     * end up owing zero — not four cents.
     */
    public Payment payInstallment() {
        requireActive();

        Breakdown due = nextBreakdown();

        outstanding = outstanding.subtract(due.amortized());
        paidInstallments++;

        if (outstanding.signum() == 0) {
            markSettled();
        }

        return new Payment(due.total(), due.interest(), due.amortized(), outstanding,
                status == LoanStatus.SETTLED);
    }

    /** What the next instalment costs, worked out without charging it. */
    public BigDecimal nextPaymentAmount() {
        requireActive();
        return nextBreakdown().total();
    }

    private record Breakdown(BigDecimal total, BigDecimal interest, BigDecimal amortized) {
    }

    /** Pure: works out the next instalment without touching anything. */
    private Breakdown nextBreakdown() {
        BigDecimal interest = Amortization.interestOn(outstanding, monthlyRate);
        boolean last = paidInstallments + 1 >= installments;

        BigDecimal total = last ? outstanding.add(interest) : installmentAmount;
        BigDecimal amortized = total.subtract(interest);

        // The same drift in the other direction: an instalment can never repay
        // more principal than is owed.
        if (amortized.compareTo(outstanding) > 0) {
            amortized = outstanding;
            total = amortized.add(interest);
        }

        return new Breakdown(total, interest, amortized);
    }

    /**
     * Pays the debt off in one go. It costs the remaining principal and nothing
     * else: the interest of the months that will now never happen is not owed.
     */
    public Payment settleEarly() {
        requireActive();

        BigDecimal amortized = outstanding;
        outstanding = BigDecimal.ZERO.setScale(2);
        markSettled();

        return new Payment(amortized, BigDecimal.ZERO.setScale(2), amortized, outstanding, true);
    }

    /** What the borrower still owes in total if they keep paying to the end. */
    public BigDecimal remainingIfPaidToTerm() {
        return installmentAmount.multiply(BigDecimal.valueOf(installments - paidInstallments));
    }

    private void markSettled() {
        status = LoanStatus.SETTLED;
        settledAt = Instant.now();
    }

    private void requireActive() {
        if (status != LoanStatus.ACTIVE) {
            throw new LoanAlreadySettledException();
        }
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public int getInstallments() {
        return installments;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public int getPaidInstallments() {
        return paidInstallments;
    }

    public BigDecimal getOutstanding() {
        return outstanding;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
