package com.pedro.bank.service;

import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PixKeyServiceTest {

    private static final String OWNER = "keys@test.com";
    private static final String OTHER = "somebody@test.com";

    /** Valid check digits; the punctuated form of the first is 529.982.247-25. */
    private static final String CPF = "52998224725";
    private static final String ANOTHER_CPF = "11144477735";

    @Autowired
    private AuthService authService;

    @Autowired
    private PixKeyService pixKeyService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void openAccounts() {
        authService.register(new RegisterRequest("Key Owner", OWNER, "password123"));
        authService.register(new RegisterRequest("Somebody Else", OTHER, "password123"));
    }

    @Test
    void aCpfIsStoredWithoutItsPunctuation() {
        PixKey key = pixKeyService.register(OWNER, PixKeyType.CPF, "529.982.247-25");

        assertThat(key.getValue()).isEqualTo(CPF);
        assertThat(PixKeyFormat.display(PixKeyType.CPF, key.getValue())).isEqualTo("529.982.247-25");
    }

    /**
     * The whole reason for normalising: without it the punctuated and unpunctuated
     * forms would be two keys, and a payer could not tell which one they were
     * about to pay.
     */
    @Test
    void thePunctuatedAndPlainFormsOfACpfAreTheSameKey() {
        pixKeyService.register(OWNER, PixKeyType.CPF, "529.982.247-25");

        assertThat(pixKeyService.resolve(CPF).getValue()).isEqualTo(CPF);
        assertThatThrownBy(() -> pixKeyService.register(OTHER, PixKeyType.CPF, CPF))
                .isInstanceOf(PixKeyAlreadyRegisteredException.class);
    }

    @Test
    void aCpfWithWrongCheckDigitsIsRefused() {
        assertThatThrownBy(() -> pixKeyService.register(OWNER, PixKeyType.CPF, "529.982.247-26"))
                .isInstanceOf(InvalidPixKeyException.class);
    }

    @Test
    void aCpfOfRepeatedDigitsIsRefused() {
        // 111.111.111-11 passes the check-digit arithmetic and is not a CPF.
        assertThatThrownBy(() -> pixKeyService.register(OWNER, PixKeyType.CPF, "11111111111"))
                .isInstanceOf(InvalidPixKeyException.class);
    }

    @Test
    void aPhoneIsStoredInItsInternationalForm() {
        PixKey key = pixKeyService.register(OWNER, PixKeyType.PHONE, "(11) 98765-4321");

        assertThat(key.getValue()).isEqualTo("+5511987654321");
        assertThat(pixKeyService.resolve("11987654321").getValue()).isEqualTo("+5511987654321");
        assertThat(pixKeyService.resolve("+55 11 98765-4321").getValue()).isEqualTo("+5511987654321");
    }

    @Test
    void anEmailKeyMustBeTheAccountsOwnEmail() {
        assertThatThrownBy(() -> pixKeyService.register(OWNER, PixKeyType.EMAIL, "someone@else.com"))
                .isInstanceOf(InvalidPixKeyException.class);

        assertThat(pixKeyService.register(OWNER, PixKeyType.EMAIL, "Keys@Test.com").getValue())
                .isEqualTo(OWNER);
    }

    @Test
    void theBankIssuesRandomKeysRatherThanTakingWhatIsAsked() {
        PixKey key = pixKeyService.register(OWNER, PixKeyType.RANDOM, "i-want-this-one");

        assertThat(key.getValue()).isNotEqualTo("i-want-this-one");
        assertThat(UUID.fromString(key.getValue())).isNotNull();
    }

    @Test
    void aSecondKeyOfTheSameIdentifyingKindIsRefused() {
        pixKeyService.register(OWNER, PixKeyType.CPF, CPF);

        assertThatThrownBy(() -> pixKeyService.register(OWNER, PixKeyType.CPF, ANOTHER_CPF))
                .isInstanceOf(PixKeyAlreadyRegisteredException.class);
    }

    @Test
    void anAccountStopsAtFiveKeys() {
        for (int i = 0; i < PixKeyService.MAX_KEYS; i++) {
            pixKeyService.register(OWNER, PixKeyType.RANDOM, null);
        }

        assertThatThrownBy(() -> pixKeyService.register(OWNER, PixKeyType.RANDOM, null))
                .isInstanceOf(PixKeyLimitReachedException.class);
    }

    @Test
    void aKeyResolvesToItsOwnersAccount() {
        pixKeyService.register(OWNER, PixKeyType.CPF, CPF);

        PixKey resolved = pixKeyService.resolve("529.982.247-25");

        assertThat(resolved.getAccount().getNumber())
                .isEqualTo(accountRepository.findByOwnerEmail(OWNER).orElseThrow().getNumber());
        assertThat(resolved.getAccount().getOwner().getName()).isEqualTo("Key Owner");
    }

    @Test
    void anUnregisteredKeyResolvesToNothing() {
        assertThatThrownBy(() -> pixKeyService.resolve(ANOTHER_CPF))
                .isInstanceOf(PixKeyNotFoundException.class);
    }

    @Test
    void somebodyElsesKeyCannotBeDeleted() {
        PixKey key = pixKeyService.register(OWNER, PixKeyType.CPF, CPF);

        assertThatThrownBy(() -> pixKeyService.delete(OTHER, key.getId()))
                .isInstanceOf(PixKeyNotFoundException.class);

        assertThat(pixKeyService.list(OWNER)).hasSize(1);
    }

    @Test
    void deletingAKeyFreesItForSomeoneElse() {
        PixKey key = pixKeyService.register(OWNER, PixKeyType.CPF, CPF);
        pixKeyService.delete(OWNER, key.getId());

        assertThat(pixKeyService.list(OWNER)).isEmpty();
        assertThat(pixKeyService.register(OTHER, PixKeyType.CPF, CPF).getValue()).isEqualTo(CPF);
    }
}
