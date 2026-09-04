package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.Amortization;
import com.pedro.bank.domain.Loan;
import com.pedro.bank.domain.LoanStatus;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final AccountService accountService;
    private final BigDecimal monthlyRate;
    private final BigDecimal maxPrincipal;
    private final List<Integer> allowedInstallments;

    public LoanService(LoanRepository loanRepository, AccountService accountService,
                       @Value("${app.loan.monthly-rate}") BigDecimal monthlyRate,
                       @Value("${app.loan.max-principal}") BigDecimal maxPrincipal,
                       @Value("${app.loan.allowed-installments}") List<Integer> allowedInstallments) {
        this.loanRepository = loanRepository;
        this.accountService = accountService;
        this.monthlyRate = monthlyRate;
        this.maxPrincipal = maxPrincipal;
        this.allowedInstallments = allowedInstallments;
    }

    /** What a loan would cost, before anyone commits to it. */
    public record Quote(BigDecimal principal, int installments, BigDecimal monthlyRate,
                        BigDecimal installmentAmount, BigDecimal total, BigDecimal totalInterest) {
    }

    public record Terms(BigDecimal maxPrincipal, BigDecimal monthlyRate,
                        List<Integer> allowedInstallments) {
    }

    /** The payment and the loan it left behind, which may now be settled. */
    public record PaymentResult(Loan.Payment payment, Loan loan) {
    }

    public Terms terms() {
        return new Terms(maxPrincipal, monthlyRate, allowedInstallments);
    }

    /**
     * Shows the cost before the money moves. Same validation as taking the loan,
     * so nothing can be quoted that could not then be taken.
     */
    public Quote simulate(BigDecimal principal, int installments) {
        requireOffered(principal, installments);

        BigDecimal installmentAmount = Amortization.installment(principal, monthlyRate, installments);
        BigDecimal total = installmentAmount.multiply(BigDecimal.valueOf(installments));

        return new Quote(principal, installments, monthlyRate, installmentAmount, total,
                total.subtract(principal));
    }

    @Transactional(readOnly = true)
    public Optional<Loan> active(String email) {
        Account account = accountService.findByOwnerEmail(email);
        return loanRepository.findByAccountIdAndStatus(account.getId(), LoanStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Loan> history(String email) {
        Account account = accountService.findByOwnerEmail(email);
        return loanRepository.findByAccountIdOrderByCreatedAtDesc(account.getId());
    }

    /** Approves on the spot and releases the money — this is a demo, not underwriting. */
    @Transactional
    public Loan take(String email, BigDecimal principal, int installments) {
        requireOffered(principal, installments);

        Account account = accountService.findByOwnerEmail(email);
        if (loanRepository.findByAccountIdAndStatus(account.getId(), LoanStatus.ACTIVE).isPresent()) {
            throw new LoanAlreadyActiveException();
        }

        Loan loan = loanRepository.save(new Loan(account, principal, monthlyRate, installments));
        accountService.credit(email, principal, "Empréstimo em " + installments + "x",
                TransactionType.LOAN_CREDIT);

        return loan;
    }

    /**
     * Charges one instalment.
     *
     * <p>The money is taken first and the debt reduced afterwards. Rolling back
     * would undo a failed payment in the database either way, but not in memory:
     * the loan entity would stay mutated for the rest of the request, and
     * anything reading it after the failure would see a debt that shrank without
     * anyone paying. Failing before touching it is simpler than reasoning about
     * that.
     */
    @Transactional
    public PaymentResult payInstallment(String email) {
        Loan loan = activeOrThrow(email);

        int number = loan.getPaidInstallments() + 1;
        accountService.debit(email, loan.nextPaymentAmount(),
                "Parcela " + number + " de " + loan.getInstallments(),
                TransactionType.LOAN_PAYMENT);

        return new PaymentResult(loan.payInstallment(), loan);
    }

    /** Pays the debt off early, which costs the remaining principal and no more. */
    @Transactional
    public PaymentResult settle(String email) {
        Loan loan = activeOrThrow(email);

        accountService.debit(email, loan.getOutstanding(), "Quitação do empréstimo",
                TransactionType.LOAN_PAYMENT);

        return new PaymentResult(loan.settleEarly(), loan);
    }

    private Loan activeOrThrow(String email) {
        return active(email).orElseThrow(NoActiveLoanException::new);
    }

    private void requireOffered(BigDecimal principal, int installments) {
        if (principal == null || principal.signum() <= 0 || principal.compareTo(maxPrincipal) > 0) {
            throw new LoanTermsNotOfferedException("principal");
        }
        if (!allowedInstallments.contains(installments)) {
            throw new LoanTermsNotOfferedException("installments");
        }
    }
}
