package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.domain.TransferRail;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.repository.TransactionRepository;
import com.pedro.bank.security.InvalidStepUpTokenException;
import com.pedro.bank.security.StepUpTokenService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.TransferRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FaceRecognitionService faceRecognitionService;
    private final StepUpTokenService stepUpTokenService;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          FaceRecognitionService faceRecognitionService,
                          StepUpTokenService stepUpTokenService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.faceRecognitionService = faceRecognitionService;
        this.stepUpTokenService = stepUpTokenService;
    }

    @Transactional(readOnly = true)
    public Account findByOwnerEmail(String email) {
        return accountRepository.findByOwnerEmail(email)
                .orElseThrow(() -> new AccountNotFoundException("owner " + email));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> statement(String email, Pageable pageable) {
        Account account = findByOwnerEmail(email);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable);
    }

    /** Moves money to an account named by its number. */
    @Transactional
    public Transaction transfer(String fromEmail, TransferRequest request) {
        Account to = accountRepository.findByNumber(request.toAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("number " + request.toAccountNumber()));

        return transfer(fromEmail, to, request.amount(), request.description(),
                request.faceToken(), TransferRail.ACCOUNT_NUMBER);
    }

    /**
     * Moves money between two accounts. Both ledger lines and both balance updates
     * happen in one database transaction, so a failure anywhere rolls the whole
     * thing back — the sender is never debited without the receiver being credited.
     *
     * <p>Every way of addressing a transfer ends up here, so a new one — a Pix key
     * today, a QR code tomorrow — cannot accidentally arrive with its own, subtly
     * different, idea of what moving money means.
     */
    @Transactional
    public Transaction transfer(String fromEmail, Account to, BigDecimal amount,
                                String description, String faceToken, TransferRail rail) {
        requireFaceConfirmation(fromEmail, faceToken);

        Account from = findByOwnerEmail(fromEmail);

        if (from.getId().equals(to.getId())) {
            throw new SameAccountTransferException();
        }

        String label = description(description, rail == TransferRail.PIX ? "Pix" : "Transfer");

        from.debit(amount);
        to.credit(amount);

        Transaction outgoing = new Transaction(
                from, rail.outgoing(), amount, label, to.getNumber());
        Transaction incoming = new Transaction(
                to, rail.incoming(), amount, label, from.getNumber());

        transactionRepository.save(incoming);
        return transactionRepository.save(outgoing);
    }

    /** Stand-in for a real funding rail — lets the demo put money into an account. */
    @Transactional
    public Transaction deposit(String email, DepositRequest request) {
        return credit(email, request.amount(), description(request.description(), "Deposit"),
                TransactionType.DEPOSIT);
    }

    /**
     * Money arriving from outside the bank — a deposit, the demo's allowance, a
     * loan being released. Everything that is not a transfer between two accounts
     * here goes through this, so a new source of funds cannot forget to write its
     * ledger line.
     */
    @Transactional
    public Transaction credit(String email, BigDecimal amount, String description,
                              TransactionType type) {
        Account account = findByOwnerEmail(email);
        account.credit(amount);

        return transactionRepository.save(
                new Transaction(account, type, amount, description, null));
    }

    /** Money leaving to somewhere that is not another account here. */
    @Transactional
    public Transaction debit(String email, BigDecimal amount, String description,
                             TransactionType type) {
        Account account = findByOwnerEmail(email);
        // Throws rather than going negative; the schema's balance >= 0 check is
        // the second line of defence behind it.
        account.debit(amount);

        return transactionRepository.save(
                new Transaction(account, type, amount, description, null));
    }

    /**
     * A user who has enrolled a face must confirm it before money moves. The
     * token is spent here, so it cannot be replayed on a second transfer.
     */
    private void requireFaceConfirmation(String email, String faceToken) {
        if (!faceRecognitionService.isEnrolled(email)) {
            return;
        }

        String verifiedEmail = stepUpTokenService.consume(
                faceToken, StepUpTokenService.Purpose.TRANSFER);
        if (!verifiedEmail.equals(email)) {
            throw new InvalidStepUpTokenException();
        }
    }

    private String description(String provided, String fallback) {
        return provided == null || provided.isBlank() ? fallback : provided;
    }
}
