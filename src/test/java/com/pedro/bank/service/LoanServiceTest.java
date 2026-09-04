package com.pedro.bank.service;

import com.pedro.bank.domain.InsufficientFundsException;
import com.pedro.bank.domain.Loan;
import com.pedro.bank.domain.LoanStatus;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanServiceTest {

    private static final String BORROWER = "borrower@test.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void openAccount() {
        authService.register(new RegisterRequest("Borrower", BORROWER, "password123"));
    }

    @Test
    void takingALoanPutsThePrincipalInTheAccount() {
        loanService.take(BORROWER, new BigDecimal("1000.00"), 12);

        assertThat(balance()).isEqualByComparingTo("1000.00");
        assertThat(accountService.statement(BORROWER, PageRequest.of(0, 10)).getContent())
                .anyMatch(t -> t.getType() == TransactionType.LOAN_CREDIT
                        && t.getAmount().compareTo(new BigDecimal("1000.00")) == 0);
    }

    @Test
    void theLoanStartsOwingExactlyWhatWasBorrowed() {
        Loan loan = loanService.take(BORROWER, new BigDecimal("1000.00"), 12);

        assertThat(loan.getOutstanding()).isEqualByComparingTo("1000.00");
        assertThat(loan.getInstallmentAmount()).isEqualByComparingTo("97.49");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    /**
     * The first instalment is mostly interest and the last is almost all
     * repayment. That shift is what a Price table is, so it is worth pinning.
     */
    @Test
    void anInstalmentIsSplitBetweenInterestAndRepayment() {
        loanService.take(BORROWER, new BigDecimal("1000.00"), 12);

        var first = loanService.payInstallment(BORROWER).payment();

        assertThat(first.interest()).isEqualByComparingTo("25.00");
        assertThat(first.amortized()).isEqualByComparingTo("72.49");
        assertThat(first.interest().add(first.amortized())).isEqualByComparingTo(first.total());
        assertThat(first.outstandingAfter()).isEqualByComparingTo("927.51");
    }

    /**
     * Rounding each instalment to centavos drifts over twelve months. A borrower
     * who has paid every instalment must owe zero, not four cents.
     */
    @Test
    void payingEveryInstalmentLeavesNothingOwed() {
        fund("5000.00");
        loanService.take(BORROWER, new BigDecimal("1000.00"), 12);

        BigDecimal repaid = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            repaid = repaid.add(loanService.payInstallment(BORROWER).payment().amortized());
        }

        assertThat(repaid).isEqualByComparingTo("1000.00");
        assertThat(loanService.active(BORROWER)).isEmpty();
        assertThat(loanService.history(BORROWER).getFirst().getStatus())
                .isEqualTo(LoanStatus.SETTLED);
        assertThat(loanService.history(BORROWER).getFirst().getOutstanding())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void settlingEarlyCostsTheRemainingPrincipalAndNotTheFutureInterest() {
        fund("5000.00");
        Loan loan = loanService.take(BORROWER, new BigDecimal("1000.00"), 12);
        loanService.payInstallment(BORROWER);

        BigDecimal ifPaidToTerm = loan.remainingIfPaidToTerm();
        var settlement = loanService.settle(BORROWER).payment();

        assertThat(settlement.total()).isEqualByComparingTo("927.51");
        assertThat(settlement.interest()).isEqualByComparingTo("0.00");
        assertThat(settlement.total()).isLessThan(ifPaidToTerm);
        assertThat(loanService.active(BORROWER)).isEmpty();
    }

    @Test
    void anInstalmentWithoutTheBalanceToCoverItChangesNothing() {
        Loan loan = loanService.take(BORROWER, new BigDecimal("1000.00"), 12);
        // Spend the principal, leaving nothing to pay the first instalment with.
        accountService.debit(BORROWER, new BigDecimal("1000.00"), "Spent it",
                TransactionType.DEPOSIT);

        assertThatThrownBy(() -> loanService.payInstallment(BORROWER))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(loanService.active(BORROWER).orElseThrow().getPaidInstallments()).isZero();
        assertThat(loanService.active(BORROWER).orElseThrow().getOutstanding())
                .isEqualByComparingTo(loan.getPrincipal());
    }

    @Test
    void onlyOneLoanRunsAtATime() {
        loanService.take(BORROWER, new BigDecimal("1000.00"), 12);

        assertThatThrownBy(() -> loanService.take(BORROWER, new BigDecimal("500.00"), 6))
                .isInstanceOf(LoanAlreadyActiveException.class);
    }

    @Test
    void aSettledLoanLeavesRoomForANewOne() {
        fund("5000.00");
        loanService.take(BORROWER, new BigDecimal("1000.00"), 12);
        loanService.settle(BORROWER);

        assertThat(loanService.take(BORROWER, new BigDecimal("500.00"), 6).getStatus())
                .isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void termsOutsideWhatIsOfferedAreRefused() {
        assertThatThrownBy(() -> loanService.take(BORROWER, new BigDecimal("50000.00"), 12))
                .isInstanceOf(LoanTermsNotOfferedException.class);
        assertThatThrownBy(() -> loanService.take(BORROWER, new BigDecimal("1000.00"), 7))
                .isInstanceOf(LoanTermsNotOfferedException.class);
        assertThatThrownBy(() -> loanService.simulate(new BigDecimal("50000.00"), 12))
                .isInstanceOf(LoanTermsNotOfferedException.class);
    }

    @Test
    void payingWithNoLoanRunningIsRefused() {
        assertThatThrownBy(() -> loanService.payInstallment(BORROWER))
                .isInstanceOf(NoActiveLoanException.class);
        assertThatThrownBy(() -> loanService.settle(BORROWER))
                .isInstanceOf(NoActiveLoanException.class);
    }

    @Test
    void theSimulationMatchesTheLoanItQuotes() {
        var quote = loanService.simulate(new BigDecimal("3000.00"), 24);
        Loan loan = loanService.take(BORROWER, new BigDecimal("3000.00"), 24);

        assertThat(quote.installmentAmount()).isEqualByComparingTo(loan.getInstallmentAmount());
        assertThat(quote.totalInterest()).isEqualByComparingTo(
                quote.total().subtract(new BigDecimal("3000.00")));
    }

    private void fund(String amount) {
        accountService.deposit(BORROWER, new DepositRequest(new BigDecimal(amount), "Funding"));
    }

    private BigDecimal balance() {
        return accountRepository.findByOwnerEmail(BORROWER).orElseThrow().getBalance();
    }
}
