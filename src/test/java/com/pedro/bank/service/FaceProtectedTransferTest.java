package com.pedro.bank.service;

import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.security.InvalidStepUpTokenException;
import com.pedro.bank.security.StepUpTokenService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import com.pedro.bank.web.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The point of enrolling a face is that money cannot move without it. These
 * cover the ways someone might try to get around that.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaceProtectedTransferTest {

    private static final String SENDER = "guarded@test.com";
    private static final String OTHER = "other@test.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private StepUpTokenService stepUpTokenService;

    private String receiverNumber;

    @BeforeEach
    void setUp() {
        authService.register(new RegisterRequest("Guarded", SENDER, "password123"));
        authService.register(new RegisterRequest("Receiver", "recv@test.com", "password123"));
        authService.register(new RegisterRequest("Other", OTHER, "password123"));

        receiverNumber = accountRepository.findByOwnerEmail("recv@test.com").orElseThrow().getNumber();
        accountService.deposit(SENDER, new DepositRequest(new BigDecimal("500.00"), null));

        faceRecognitionService.enroll(SENDER, descriptor(7));
    }

    @Test
    void anEnrolledUserCannotTransferWithoutConfirmingTheirFace() {
        assertThatThrownBy(() -> accountService.transfer(SENDER, transfer(null)))
                .isInstanceOf(InvalidStepUpTokenException.class);

        assertThat(balanceOf(SENDER)).isEqualByComparingTo("500.00");
    }

    @Test
    void aValidFaceTokenLetsTheTransferThrough() {
        String token = stepUpTokenService.issue(SENDER, StepUpTokenService.Purpose.TRANSFER);

        accountService.transfer(SENDER, transfer(token));

        assertThat(balanceOf(SENDER)).isEqualByComparingTo("400.00");
    }

    @Test
    void aFaceTokenCannotBeReplayedOnASecondTransfer() {
        String token = stepUpTokenService.issue(SENDER, StepUpTokenService.Purpose.TRANSFER);
        accountService.transfer(SENDER, transfer(token));

        assertThatThrownBy(() -> accountService.transfer(SENDER, transfer(token)))
                .isInstanceOf(InvalidStepUpTokenException.class);

        assertThat(balanceOf(SENDER)).isEqualByComparingTo("400.00");
    }

    @Test
    void aTokenIssuedForAnotherUserIsRejected() {
        String othersToken = stepUpTokenService.issue(OTHER, StepUpTokenService.Purpose.TRANSFER);

        assertThatThrownBy(() -> accountService.transfer(SENDER, transfer(othersToken)))
                .isInstanceOf(InvalidStepUpTokenException.class);

        assertThat(balanceOf(SENDER)).isEqualByComparingTo("500.00");
    }

    @Test
    void aLoginTokenCannotStandInForATransferConfirmation() {
        String loginToken = stepUpTokenService.issue(SENDER, StepUpTokenService.Purpose.LOGIN);

        assertThatThrownBy(() -> accountService.transfer(SENDER, transfer(loginToken)))
                .isInstanceOf(InvalidStepUpTokenException.class);
    }

    @Test
    void aUserWithoutAnEnrolledFaceTransfersAsBefore() {
        accountService.deposit(OTHER, new DepositRequest(new BigDecimal("50.00"), null));

        accountService.transfer(OTHER, new TransferRequest(
                receiverNumber, new BigDecimal("10.00"), "No face", null));

        assertThat(balanceOf(OTHER)).isEqualByComparingTo("40.00");
    }

    private TransferRequest transfer(String faceToken) {
        return new TransferRequest(receiverNumber, new BigDecimal("100.00"), "Rent", faceToken);
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
