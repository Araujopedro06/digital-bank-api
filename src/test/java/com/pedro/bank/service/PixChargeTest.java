package com.pedro.bank.service;

import com.pedro.bank.domain.PixCharge;
import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A charge is what a shareable QR points at. What matters is that the link
 * carries nothing but an opaque id, and that it stops working when it should.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PixChargeTest {

    private static final String PAYEE = "charged@test.com";
    private static final String STRANGER = "stranger@test.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private PixKeyService pixKeyService;

    @Autowired
    private PixService pixService;

    private PixKey key;

    @BeforeEach
    void setUp() {
        authService.register(new RegisterRequest("Maria Silva", PAYEE, "password123"));
        authService.register(new RegisterRequest("Stranger", STRANGER, "password123"));

        key = pixKeyService.register(PAYEE, PixKeyType.PHONE, "(11) 91234-5678");
    }

    @Test
    void aLinkOpensToTheOwnerOfTheKeyBehindIt() {
        var created = pixService.createCharge(PAYEE, key.getId(), new BigDecimal("35.00"), "Conta");

        PixCharge opened = pixService.openCharge(created.charge().getId());

        assertThat(opened.getKey().getValue()).isEqualTo("+5511912345678");
        assertThat(opened.getKey().getAccount().getOwner().getName()).isEqualTo("Maria Silva");
        assertThat(opened.getAmount()).isEqualByComparingTo("35.00");
        assertThat(opened.getDescription()).isEqualTo("Conta");
    }

    /**
     * The whole reason the link holds an id instead of the key: the id is drawn
     * at random rather than derived from the key, so it says nothing about whose
     * phone number or CPF is behind it.
     */
    @Test
    void theSharedIdIsRandomRatherThanDerivedFromTheKey() {
        var first = pixService.createCharge(PAYEE, key.getId(), null, null);
        var second = pixService.createCharge(PAYEE, key.getId(), null, null);

        assertThat(first.charge().getId()).isNotEqualTo(second.charge().getId());
        assertThat(first.charge().getId().version()).isEqualTo(4);
    }

    @Test
    void aLinkWithoutAnAmountLetsThePayerChooseOne() {
        var created = pixService.createCharge(PAYEE, key.getId(), null, null);

        assertThat(pixService.openCharge(created.charge().getId()).getAmount()).isNull();
    }

    @Test
    void aChargeAlsoYieldsTheStandardCopiaECola() {
        var created = pixService.createCharge(PAYEE, key.getId(), new BigDecimal("35.00"), "Conta");

        assertThat(BrCode.parse(created.brCode()).key()).isEqualTo(key.getValue());
        assertThat(BrCode.parse(created.brCode()).amount()).isEqualByComparingTo("35.00");
    }

    @Test
    void aChargeCannotBeCreatedForSomebodyElsesKey() {
        assertThatThrownBy(() -> pixService.createCharge(STRANGER, key.getId(), null, null))
                .isInstanceOf(PixKeyNotFoundException.class);
    }

    @Test
    void anUnknownLinkIsADeadEnd() {
        assertThatThrownBy(() -> pixService.openCharge(UUID.randomUUID()))
                .isInstanceOf(PixChargeNotFoundException.class);
    }

    /** Expiry is a boundary, so it is worth pinning both sides of it. */
    @Test
    void aChargeIsLiveUntilItsExpiryAndNotAfter() {
        Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);
        PixCharge charge = new PixCharge(key, null, null, expiry);

        assertThat(charge.hasExpired(expiry.minusSeconds(1))).isFalse();
        assertThat(charge.hasExpired(expiry)).isTrue();
        assertThat(charge.hasExpired(expiry.plusSeconds(1))).isTrue();
    }

    /**
     * Giving up a key has to kill the links pointing at it, or a QR on a wall
     * would keep resolving to an address its owner has abandoned.
     */
    @Test
    void givingUpTheKeyKillsItsLinks() {
        var created = pixService.createCharge(PAYEE, key.getId(), null, null);

        pixKeyService.delete(PAYEE, key.getId());

        assertThatThrownBy(() -> pixService.openCharge(created.charge().getId()))
                .isInstanceOf(PixChargeNotFoundException.class);
    }
}
