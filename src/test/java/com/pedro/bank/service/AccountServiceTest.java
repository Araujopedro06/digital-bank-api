package com.pedro.bank.service;

import com.pedro.bank.domain.InsufficientFundsException;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import com.pedro.bank.web.dto.TransferRequest;
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
class AccountServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private String senderNumber;
    private String receiverNumber;

    @BeforeEach
    void openTwoAccounts() {
        authService.register(new RegisterRequest("Sender", "sender@test.com", "password123"));
        authService.register(new RegisterRequest("Receiver", "receiver@test.com", "password123"));

        senderNumber = accountRepository.findByOwnerEmail("sender@test.com").orElseThrow().getNumber();
        receiverNumber = accountRepository.findByOwnerEmail("receiver@test.com").orElseThrow().getNumber();

        accountService.deposit("sender@test.com", new DepositRequest(new BigDecimal("100.00"), null));
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        accountService.transfer("sender@test.com",
                new TransferRequest(receiverNumber, new BigDecimal("30.00"), "Rent", null));

        assertThat(accountRepository.findByNumber(senderNumber).orElseThrow().getBalance())
                .isEqualByComparingTo("70.00");
        assertThat(accountRepository.findByNumber(receiverNumber).orElseThrow().getBalance())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void transferWritesOneLedgerLineOnEachSide() {
        accountService.transfer("sender@test.com",
                new TransferRequest(receiverNumber, new BigDecimal("30.00"), "Rent", null));

        var senderStatement = accountService.statement("sender@test.com", PageRequest.of(0, 10));
        var receiverStatement = accountService.statement("receiver@test.com", PageRequest.of(0, 10));

        assertThat(senderStatement.getContent())
                .anyMatch(t -> t.getType() == TransactionType.TRANSFER_OUT
                        && t.getCounterpartyNumber().equals(receiverNumber));
        assertThat(receiverStatement.getContent())
                .anyMatch(t -> t.getType() == TransactionType.TRANSFER_IN
                        && t.getCounterpartyNumber().equals(senderNumber));
    }

    @Test
    void transferAboveBalanceIsRejected() {
        assertThatThrownBy(() -> accountService.transfer("sender@test.com",
                new TransferRequest(receiverNumber, new BigDecimal("100.01"), "Too much", null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountRepository.findByNumber(senderNumber).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void transferToOwnAccountIsRejected() {
        assertThatThrownBy(() -> accountService.transfer("sender@test.com",
                new TransferRequest(senderNumber, new BigDecimal("10.00"), "Self", null)))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void transferToUnknownAccountIsRejected() {
        assertThatThrownBy(() -> accountService.transfer("sender@test.com",
                new TransferRequest("000000000", new BigDecimal("10.00"), "Ghost", null)))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
