package com.pedro.bank.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Spring here: a Pix code is string handling, and the point of these is that
 * a real banking app would accept what {@link BrCode} produces.
 */
class BrCodeTest {

    private static final String KEY = "pedro@demo.com";

    /**
     * The published check value for CRC-16/CCITT-FALSE. Everything else in this
     * class is a round trip through code I wrote, which would agree with itself
     * even if the checksum were wrong in a way no bank would accept — this is the
     * one assertion anchored outside the implementation.
     */
    @Test
    void checksumMatchesTheStandardCheckValue() {
        assertThat(BrCode.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void aCodeCarriesTheKeyAmountAndDescriptionBack() {
        String payload = BrCode.build(KEY, "Pedro Araujo", "SAO PAULO",
                new BigDecimal("42.50"), "Almoco");

        BrCode.Parsed parsed = BrCode.parse(payload);

        assertThat(parsed.key()).isEqualTo(KEY);
        assertThat(parsed.amount()).isEqualByComparingTo("42.50");
        assertThat(parsed.description()).isEqualTo("Almoco");
        assertThat(parsed.merchantName()).isEqualTo("PEDRO ARAUJO");
        assertThat(parsed.merchantCity()).isEqualTo("SAO PAULO");
    }

    @Test
    void theCodeStartsAndEndsTheWayThePayloadSpecSays() {
        String payload = BrCode.build(KEY, "Pedro", "SAO PAULO", null, null);

        assertThat(payload).startsWith("000201");
        assertThat(payload).contains("br.gov.bcb.pix");
        // The checksum is the last field, and it is four hexadecimal digits.
        assertThat(payload.substring(payload.length() - 8)).matches("6304[0-9A-F]{4}");
    }

    @Test
    void aCodeWithoutAnAmountLetsThePayerChooseOne() {
        String payload = BrCode.build(KEY, "Pedro", "SAO PAULO", null, null);

        assertThat(BrCode.parse(payload).amount()).isNull();
        // Field 54 is the amount, and it must be absent rather than zero. The
        // checksum is excluded from the search: it is four free hexadecimal
        // digits and could spell 5404 on its own.
        assertThat(payload.substring(0, payload.length() - 8)).doesNotContain("5404");
    }

    @Test
    void aSingleAlteredCharacterIsRejected() {
        String payload = BrCode.build(KEY, "Pedro", "SAO PAULO", new BigDecimal("10.00"), null);
        String tampered = payload.replace("10.00", "90.00");

        assertThatThrownBy(() -> BrCode.parse(tampered))
                .isInstanceOf(InvalidBrCodeException.class)
                .hasMessageContaining("Checksum");
    }

    @Test
    void aTruncatedCodeIsRejected() {
        String payload = BrCode.build(KEY, "Pedro", "SAO PAULO", null, null);

        assertThatThrownBy(() -> BrCode.parse(payload.substring(0, payload.length() - 3)))
                .isInstanceOf(InvalidBrCodeException.class);
    }

    @Test
    void accentsAreFoldedRatherThanDropped() {
        String payload = BrCode.build(KEY, "José Antônio Gonçalves", "SÃO PAULO", null, null);

        assertThat(BrCode.parse(payload).merchantName()).isEqualTo("JOSE ANTONIO GONCALVES");
        assertThat(BrCode.parse(payload).merchantCity()).isEqualTo("SAO PAULO");
    }

    @Test
    void aNameLongerThanTheFieldAllowsIsTrimmedNotRefused() {
        String payload = BrCode.build(KEY, "Maria Fernanda Rodrigues do Nascimento Silva",
                "SAO PAULO", null, null);

        assertThat(BrCode.parse(payload).merchantName()).hasSizeLessThanOrEqualTo(25);
    }

    /**
     * The key and the description share one 99-character field. A maximum-length
     * e-mail key fills it alone, and the encoder has to drop the note rather than
     * write a length no reader can parse.
     */
    @Test
    void aLongKeyCostsTheDescriptionAndNotTheCode() {
        String longKey = "a".repeat(64) + "@example.com";
        assertThat(longKey).hasSize(76);

        String payload = BrCode.build(longKey, "Pedro", "SAO PAULO", null, "Uma descricao longa");

        BrCode.Parsed parsed = BrCode.parse(payload);
        assertThat(parsed.key()).isEqualTo(longKey);
        assertThat(parsed.description()).isEmpty();
    }

    @Test
    void somethingThatIsNotAPixCodeIsRejected() {
        assertThatThrownBy(() -> BrCode.parse("hello"))
                .isInstanceOf(InvalidBrCodeException.class);
    }
}
