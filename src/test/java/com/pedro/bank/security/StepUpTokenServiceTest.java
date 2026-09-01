package com.pedro.bank.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepUpTokenServiceTest {

    private final StepUpTokenService service = new StepUpTokenService();

    @Test
    void aTokenIsSpentOnFirstUse() {
        String token = service.issue("user@test.com", StepUpTokenService.Purpose.TRANSFER);

        assertThat(service.consume(token, StepUpTokenService.Purpose.TRANSFER))
                .isEqualTo("user@test.com");

        assertThatThrownBy(() -> service.consume(token, StepUpTokenService.Purpose.TRANSFER))
                .isInstanceOf(InvalidStepUpTokenException.class);
    }

    @Test
    void aLoginTokenCannotBeUsedToConfirmATransfer() {
        String token = service.issue("user@test.com", StepUpTokenService.Purpose.LOGIN);

        assertThatThrownBy(() -> service.consume(token, StepUpTokenService.Purpose.TRANSFER))
                .isInstanceOf(InvalidStepUpTokenException.class);
    }

    @Test
    void anUnknownOrMissingTokenIsRejected() {
        assertThatThrownBy(() -> service.consume("made-up", StepUpTokenService.Purpose.TRANSFER))
                .isInstanceOf(InvalidStepUpTokenException.class);
        assertThatThrownBy(() -> service.consume(null, StepUpTokenService.Purpose.TRANSFER))
                .isInstanceOf(InvalidStepUpTokenException.class);
    }

    @Test
    void tokensAreDistinctPerIssue() {
        String first = service.issue("user@test.com", StepUpTokenService.Purpose.LOGIN);
        String second = service.issue("user@test.com", StepUpTokenService.Purpose.LOGIN);

        assertThat(first).isNotEqualTo(second);
    }
}
