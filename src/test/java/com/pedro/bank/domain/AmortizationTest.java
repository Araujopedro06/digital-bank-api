package com.pedro.bank.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmortizationTest {

    private static final BigDecimal RATE = new BigDecimal("0.025");

    /**
     * R$ 1.000 over 12 months at 2.5% a month. Worked out from the Price formula
     * rather than from this implementation, so the two have to agree.
     */
    @Test
    void theInstalmentMatchesThePriceFormula() {
        BigDecimal installment =
                Amortization.installment(new BigDecimal("1000.00"), RATE, 12);

        assertThat(installment).isEqualByComparingTo("97.49");
    }

    @Test
    void oneInstalmentIsThePrincipalPlusOneMonthOfInterest() {
        BigDecimal installment =
                Amortization.installment(new BigDecimal("1000.00"), RATE, 1);

        assertThat(installment).isEqualByComparingTo("1025.00");
    }

    @Test
    void anInterestFreeLoanIsJustThePrincipalSplitEvenly() {
        BigDecimal installment =
                Amortization.installment(new BigDecimal("1200.00"), BigDecimal.ZERO, 12);

        assertThat(installment).isEqualByComparingTo("100.00");
    }

    @Test
    void payingOverLongerCostsMoreInTotalAndLessEachMonth() {
        BigDecimal principal = new BigDecimal("3000.00");

        BigDecimal shortTerm = Amortization.installment(principal, RATE, 6);
        BigDecimal longTerm = Amortization.installment(principal, RATE, 24);

        assertThat(longTerm).isLessThan(shortTerm);
        assertThat(longTerm.multiply(BigDecimal.valueOf(24)))
                .isGreaterThan(shortTerm.multiply(BigDecimal.valueOf(6)));
    }

    @Test
    void interestIsChargedOnWhatIsStillOwed() {
        assertThat(Amortization.interestOn(new BigDecimal("1000.00"), RATE))
                .isEqualByComparingTo("25.00");
        assertThat(Amortization.interestOn(BigDecimal.ZERO.setScale(2), RATE))
                .isEqualByComparingTo("0.00");
    }
}
