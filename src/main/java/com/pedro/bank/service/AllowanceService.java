package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * The demo's way of handing a new account some money: a fictional rich aunt who
 * is generous up to a point, stingy past it, and tells you to get a job past
 * that.
 *
 * <p>It exists because the app is useless empty — someone who has just opened an
 * account cannot try a Pix with a zero balance, and "here is a deposit button"
 * is a worse first impression than a joke that works.
 */
@Service
public class AllowanceService {

    /** What the aunt decided. The wording of it is the client's business. */
    public enum Outcome {
        /** She sent the whole amount. */
        GRANTED,
        /** She sent what she felt like sending, which is less than was asked. */
        HAGGLED,
        /** She sent nothing and had something to say about it. */
        REFUSED
    }

    private static final String LEDGER_DESCRIPTION = "Presente da tia Odete";

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final BigDecimal generousLimit;
    private final BigDecimal haggleLimit;
    private final Duration cooldown;

    public AllowanceService(AccountService accountService,
                            TransactionRepository transactionRepository,
                            @Value("${app.demo.allowance.generous-limit}") BigDecimal generousLimit,
                            @Value("${app.demo.allowance.haggle-limit}") BigDecimal haggleLimit,
                            @Value("${app.demo.allowance.cooldown}") Duration cooldown) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.generousLimit = generousLimit;
        this.haggleLimit = haggleLimit;
        this.cooldown = cooldown;
    }

    public record Result(Outcome outcome, BigDecimal asked, BigDecimal granted,
                         BigDecimal balance, Instant availableAt) {
    }

    public record Status(Instant availableAt, BigDecimal generousLimit, BigDecimal haggleLimit) {
    }

    @Transactional(readOnly = true)
    public Status status(String email) {
        return new Status(availableAt(accountService.findByOwnerEmail(email)),
                generousLimit, haggleLimit);
    }

    @Transactional
    public Result request(String email, BigDecimal asked) {
        Account account = accountService.findByOwnerEmail(email);

        Instant availableAt = availableAt(account);
        if (availableAt != null && Instant.now().isBefore(availableAt)) {
            throw new AllowanceTooSoonException(availableAt);
        }

        // Asking for the moon costs nothing but the answer. No ledger line is
        // written, so a refusal does not start the cooldown — the next attempt
        // with a sensible number should not have to wait.
        if (asked.compareTo(haggleLimit) > 0) {
            return new Result(Outcome.REFUSED, asked, BigDecimal.ZERO.setScale(2),
                    account.getBalance(), availableAt);
        }

        BigDecimal granted = asked.min(generousLimit);
        Outcome outcome = granted.compareTo(asked) < 0 ? Outcome.HAGGLED : Outcome.GRANTED;

        Transaction credited = accountService.credit(
                email, granted, LEDGER_DESCRIPTION, TransactionType.ALLOWANCE);

        return new Result(outcome, asked, granted, credited.getBalanceAfter(),
                credited.getCreatedAt().plus(cooldown));
    }

    /**
     * When she will pick up the phone again, read from the ledger rather than a
     * table of its own: the last time she gave is already recorded there.
     */
    private Instant availableAt(Account account) {
        return transactionRepository
                .findFirstByAccountIdAndTypeOrderByCreatedAtDesc(
                        account.getId(), TransactionType.ALLOWANCE)
                .map(last -> last.getCreatedAt().plus(cooldown))
                .filter(Instant.now()::isBefore)
                .orElse(null);
    }
}
