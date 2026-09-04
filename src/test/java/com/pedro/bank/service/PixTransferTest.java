package com.pedro.bank.service;

import com.pedro.bank.domain.InsufficientFundsException;
import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.domain.TransactionType;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.security.InvalidStepUpTokenException;
import com.pedro.bank.security.StepUpTokenService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.PixTransferRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paying a key has to be the same transfer as paying an account number — same
 * atomicity, same face requirement — with only the addressing and the labels
 * different.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PixTransferTest {

    private static final String PAYER = "payer@test.com";
    private static final String PAYEE = "payee@test.com";
    private static final String PAYEE_KEY = "+5511912345678";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PixKeyService pixKeyService;

    @Autowired
    private PixService pixService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private StepUpTokenService stepUpTokenService;

    @BeforeEach
    void setUp() {
        authService.register(new RegisterRequest("Payer", PAYER, "password123"));
        authService.register(new RegisterRequest("Maria Silva", PAYEE, "password123"));

        accountService.deposit(PAYER, new DepositRequest(new BigDecimal("300.00"), null));
        pixKeyService.register(PAYEE, PixKeyType.PHONE, "(11) 91234-5678");
    }

    @Test
    void payingAKeyMovesTheMoneyToItsOwner() {
        pixService.pay(PAYER, pix(PAYEE_KEY, "50.00", null));

        assertThat(balanceOf(PAYER)).isEqualByComparingTo("250.00");
        assertThat(balanceOf(PAYEE)).isEqualByComparingTo("50.00");
    }

    @Test
    void bothSidesSeeItAsAPixRatherThanAPlainTransfer() {
        pixService.pay(PAYER, pix(PAYEE_KEY, "50.00", "Almoço"));

        assertThat(accountService.statement(PAYER, PageRequest.of(0, 10)).getContent())
                .anyMatch(t -> t.getType() == TransactionType.PIX_OUT
                        && t.getDescription().equals("Almoço"));
        assertThat(accountService.statement(PAYEE, PageRequest.of(0, 10)).getContent())
                .anyMatch(t -> t.getType() == TransactionType.PIX_IN);
    }

    @Test
    void aPixWithoutADescriptionIsStillLabelled() {
        pixService.pay(PAYER, pix(PAYEE_KEY, "10.00", null));

        assertThat(accountService.statement(PAYER, PageRequest.of(0, 10)).getContent())
                .anyMatch(t -> t.getType() == TransactionType.PIX_OUT
                        && t.getDescription().equals("Pix"));
    }

    @Test
    void thePunctuatedFormOfTheKeyReachesTheSameAccount() {
        pixService.pay(PAYER, pix("(11) 91234-5678", "25.00", null));

        assertThat(balanceOf(PAYEE)).isEqualByComparingTo("25.00");
    }

    @Test
    void payingAnUnregisteredKeyMovesNothing() {
        assertThatThrownBy(() -> pixService.pay(PAYER, pix("+5511999999999", "10.00", null)))
                .isInstanceOf(PixKeyNotFoundException.class);

        assertThat(balanceOf(PAYER)).isEqualByComparingTo("300.00");
    }

    @Test
    void payingYourOwnKeyIsRefused() {
        PixKey mine = pixKeyService.register(PAYER, PixKeyType.RANDOM, null);

        assertThatThrownBy(() -> pixService.pay(PAYER, pix(mine.getValue(), "10.00", null)))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void aPixAboveTheBalanceIsRefused() {
        assertThatThrownBy(() -> pixService.pay(PAYER, pix(PAYEE_KEY, "300.01", null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(PAYER)).isEqualByComparingTo("300.00");
        assertThat(balanceOf(PAYEE)).isEqualByComparingTo("0.00");
    }

    /** The face requirement guards the money, so a new way to address it changes nothing. */
    @Test
    void anEnrolledUserStillHasToConfirmTheirFace() {
        faceRecognitionService.enroll(PAYER, descriptor(11));

        assertThatThrownBy(() -> pixService.pay(PAYER, pix(PAYEE_KEY, "10.00", null)))
                .isInstanceOf(InvalidStepUpTokenException.class);

        String token = stepUpTokenService.issue(PAYER, StepUpTokenService.Purpose.TRANSFER);
        pixService.pay(PAYER, new PixTransferRequest(
                PAYEE_KEY, new BigDecimal("10.00"), null, token));

        assertThat(balanceOf(PAYER)).isEqualByComparingTo("290.00");
    }

    @Test
    void aGeneratedCodeIsReadBackAsTheKeyItPays() {
        PixKey key = pixKeyService.list(PAYEE).getFirst();

        var code = pixService.brCodeFor(PAYEE, key.getId(), new BigDecimal("35.00"), "Conta");
        var parsed = pixService.parse(code.payload());

        assertThat(parsed.key().getValue()).isEqualTo(PAYEE_KEY);
        assertThat(parsed.key().getAccount().getOwner().getName()).isEqualTo("Maria Silva");
        assertThat(parsed.amount()).isEqualByComparingTo("35.00");
        assertThat(parsed.description()).isEqualTo("Conta");
    }

    @Test
    void aCodeCannotBeIssuedForSomebodyElsesKey() {
        PixKey theirs = pixKeyService.list(PAYEE).getFirst();

        assertThatThrownBy(() -> pixService.brCodeFor(PAYER, theirs.getId(), null, null))
                .isInstanceOf(PixKeyNotFoundException.class);
    }

    private PixTransferRequest pix(String key, String amount, String description) {
        return new PixTransferRequest(key, new BigDecimal(amount), description, null);
    }

    private BigDecimal balanceOf(String email) {
        return accountRepository.findByOwnerEmail(email).orElseThrow().getBalance();
    }

    private double[] descriptor(long seed) {
        Random random = new Random(seed);
        double[] values = new double[128];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextDouble() - 0.5;
        }
        return values;
    }
}
