package com.pedro.bank.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The Price table: every instalment is the same size, and the split inside it
 * moves over time — mostly interest at the start, mostly repayment at the end.
 *
 * <p>Money is rounded to centavos only at the edges. The compounding itself runs
 * at twenty digits, because rounding a rate to two decimals and then raising it
 * to the twenty-fourth power is how a payment schedule ends up several reais away
 * from what the borrower was quoted.
 */
public final class Amortization {

    private static final MathContext MATH = new MathContext(20, RoundingMode.HALF_UP);

    private Amortization() {
    }

    /**
     * {@code P · i · (1+i)ⁿ / ((1+i)ⁿ − 1)} — the fixed instalment that pays off
     * {@code principal} in {@code installments} periods at {@code monthlyRate}.
     */
    public static BigDecimal installment(BigDecimal principal, BigDecimal monthlyRate,
                                         int installments) {
        if (installments < 1) {
            throw new IllegalArgumentException("A loan needs at least one instalment");
        }
        // An interest-free loan is just the principal split evenly; the formula
        // below would divide by zero on it.
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
        }

        BigDecimal compound = BigDecimal.ONE.add(monthlyRate).pow(installments, MATH);

        return principal.multiply(monthlyRate, MATH)
                .multiply(compound, MATH)
                .divide(compound.subtract(BigDecimal.ONE), MATH)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** One period's interest on what is still owed. */
    public static BigDecimal interestOn(BigDecimal outstanding, BigDecimal monthlyRate) {
        return outstanding.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
    }
}
