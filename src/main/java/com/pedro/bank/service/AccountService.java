package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransactionType;
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

    /**
     * Moves money between two accounts. Both ledger lines and both balance updates
     * happen in one database transaction, so a failure anywhere rolls the whole
     * thing back — the sender is never debited without the receiver being credited.
     */
    @Transactional
    public Transaction transfer(String fromEmail, TransferRequest request) {
        requireFaceConfirmation(fromEmail, request.faceToken());

        Account from = findByOwnerEmail(fromEmail);
        Account to = accountRepository.findByNumber(request.toAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("number " + request.toAccountNumber()));

        if (from.getId().equals(to.getId())) {
            throw new SameAccountTransferException();
        }

        String description = description(request.description(), "Transfer");

        from.debit(request.amount());
        to.credit(request.amount());

        Transaction outgoing = new Transaction(
                from, TransactionType.TRANSFER_OUT, request.amount(), description, to.getNumber());
        Transaction incoming = new Transaction(
                to, TransactionType.TRANSFER_IN, request.amount(), description, from.getNumber());

        transactionRepository.save(incoming);
        return transactionRepository.save(outgoing);
    }

    /** Stand-in for a real funding rail — lets the demo put money into an account. */
    @Transactional
    public Transaction deposit(String email, DepositRequest request) {
        Account account = findByOwnerEmail(email);
        account.credit(request.amount());

        return transactionRepository.save(new Transaction(
                account, TransactionType.DEPOSIT, request.amount(),
                description(request.description(), "Deposit"), null));
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
