package com.pedro.bank.service;

import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.AccountRepository;
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

/**
 * The joke has to be a real funding rail underneath: it moves money, so its
 * limits are worth the same care as anything else that does.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AllowanceServiceTest {

    private static final String NEPHEW = "nephew@test.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AllowanceService allowanceService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void openAccount() {
        authService.register(new RegisterRequest("Nephew", NEPHEW, "password123"));
    }

    @Test
    void aModestRequestIsPaidInFull() {
        var result = allowanceService.request(NEPHEW, new BigDecimal("200.00"));

        assertThat(result.outcome()).isEqualTo(AllowanceService.Outcome.GRANTED);
        assertThat(result.granted()).isEqualByComparingTo("200.00");
        assertThat(balance()).isEqualByComparingTo("200.00");
    }

    @Test
    void askingForMoreThanSheGivesGetsWhatSheGives() {
        var result = allowanceService.request(NEPHEW, new BigDecimal("1500.00"));

        assertThat(result.outcome()).isEqualTo(AllowanceService.Outcome.HAGGLED);
        assertThat(result.granted()).isEqualByComparingTo("500.00");
        assertThat(result.asked()).isEqualByComparingTo("1500.00");
        assertThat(balance()).isEqualByComparingTo("500.00");
    }

    @Test
    void askingForTheMoonGetsNothingAndNoLedgerLine() {
        var result = allowanceService.request(NEPHEW, new BigDecimal("999999.00"));

        assertThat(result.outcome()).isEqualTo(AllowanceService.Outcome.REFUSED);
        assertThat(result.granted()).isEqualByComparingTo("0.00");
        assertThat(balance()).isEqualByComparingTo("0.00");
        assertThat(accountService.statement(NEPHEW, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    /** Being told to get a job should not also cost you the next five minutes. */
    @Test
    void aRefusalDoesNotStartTheCooldown() {
        allowanceService.request(NEPHEW, new BigDecimal("999999.00"));

        var second = allowanceService.request(NEPHEW, new BigDecimal("100.00"));

        assertThat(second.outcome()).isEqualTo(AllowanceService.Outcome.GRANTED);
        assertThat(balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void sheWillNotBeAskedTwiceInARow() {
        allowanceService.request(NEPHEW, new BigDecimal("100.00"));

        assertThatThrownBy(() -> allowanceService.request(NEPHEW, new BigDecimal("100.00")))
                .isInstanceOf(AllowanceTooSoonException.class);

        assertThat(balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void theStatusSaysWhenSheIsFreeAgain() {
        assertThat(allowanceService.status(NEPHEW).availableAt()).isNull();

        allowanceService.request(NEPHEW, new BigDecimal("100.00"));

        assertThat(allowanceService.status(NEPHEW).availableAt()).isNotNull();
        assertThat(allowanceService.status(NEPHEW).generousLimit()).isEqualByComparingTo("500.00");
    }

    @Test
    void whatSheGivesLandsInTheStatementAsItsOwnKindOfMoney() {
        allowanceService.request(NEPHEW, new BigDecimal("250.00"));

        assertThat(accountService.statement(NEPHEW, PageRequest.of(0, 10)).getContent())
                .anyMatch(t -> t.getType() == TransactionType.ALLOWANCE
                        && t.getAmount().compareTo(new BigDecimal("250.00")) == 0);
    }

    private BigDecimal balance() {
        return accountRepository.findByOwnerEmail(NEPHEW).orElseThrow().getBalance();
    }
}
