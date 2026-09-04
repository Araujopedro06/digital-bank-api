package com.pedro.bank.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Pix copia e cola" string, and the payload behind a Pix QR code.
 *
 * <p>It is an EMV® QR Code: a flat list of {@code IDVVdata} groups — two digits
 * of identifier, two digits of length, then exactly that many characters — where
 * a few of the groups contain another such list. The last group is a CRC16 over
 * everything before it, its own identifier and length included, which is what
 * lets a banking app reject a code that lost a character on its way through a
 * chat message.
 *
 * <p>Written out rather than pulled from a library because it is a hundred lines
 * of string handling, and depending on someone else's build of it would hide the
 * one part worth understanding.
 */
public final class BrCode {

    private static final String PIX_GUI = "br.gov.bcb.pix";

    private static final String ID_PAYLOAD_FORMAT = "00";
    private static final String ID_INITIATION_METHOD = "01";
    private static final String ID_MERCHANT_ACCOUNT = "26";
    private static final String ID_MERCHANT_CATEGORY = "52";
    private static final String ID_CURRENCY = "53";
    private static final String ID_AMOUNT = "54";
    private static final String ID_COUNTRY = "58";
    private static final String ID_MERCHANT_NAME = "59";
    private static final String ID_MERCHANT_CITY = "60";
    private static final String ID_ADDITIONAL_DATA = "62";
    private static final String ID_CRC = "63";

    private static final String SUB_GUI = "00";
    private static final String SUB_KEY = "01";
    private static final String SUB_DESCRIPTION = "02";
    private static final String SUB_REFERENCE_LABEL = "05";

    /** Money, ISO 4217 numeric for BRL. */
    private static final String CURRENCY_BRL = "986";
    /** "Not a specific line of business", which is what a person receiving money is. */
    private static final String CATEGORY_NONE = "0000";
    /** 11 = static, may be paid more than once. A single-use code would be 12. */
    private static final String STATIC_CODE = "11";
    /** The spec wants a reference; three asterisks is its "none given". */
    private static final String NO_REFERENCE = "***";

    private static final int MAX_NAME = 25;
    private static final int MAX_CITY = 15;
    private static final int MAX_DESCRIPTION = 40;

    /** A field's length is written in two digits, so nothing may be longer. */
    private static final int MAX_FIELD = 99;

    private BrCode() {
    }

    /** Everything a payer's app can read back out of a code. */
    public record Parsed(String key, BigDecimal amount, String description,
                         String merchantName, String merchantCity) {
    }

    public static String build(String key, String merchantName, String merchantCity,
                               BigDecimal amount, String description) {
        StringBuilder merchantAccount = new StringBuilder()
                .append(field(SUB_GUI, PIX_GUI))
                .append(field(SUB_KEY, key));

        // The description shares the merchant-account field with the key, and
        // that field cannot exceed 99 characters. A 77-character e-mail key
        // fills it on its own, so the description is trimmed to whatever is left
        // and dropped when nothing is: a code that pays the right person without
        // its note beats a code no app will read.
        int roomForDescription = MAX_FIELD - merchantAccount.length() - 4;
        String cleanDescription =
                ascii(description, Math.min(MAX_DESCRIPTION, Math.max(roomForDescription, 0)), false);
        if (!cleanDescription.isEmpty()) {
            merchantAccount.append(field(SUB_DESCRIPTION, cleanDescription));
        }

        StringBuilder payload = new StringBuilder()
                .append(field(ID_PAYLOAD_FORMAT, "01"))
                .append(field(ID_INITIATION_METHOD, STATIC_CODE))
                .append(field(ID_MERCHANT_ACCOUNT, merchantAccount.toString()))
                .append(field(ID_MERCHANT_CATEGORY, CATEGORY_NONE))
                .append(field(ID_CURRENCY, CURRENCY_BRL));

        // An amount is optional: without it the payer types how much to send.
        if (amount != null && amount.signum() > 0) {
            payload.append(field(ID_AMOUNT, amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }

        payload.append(field(ID_COUNTRY, "BR"))
                .append(field(ID_MERCHANT_NAME, ascii(merchantName, MAX_NAME, true)))
                .append(field(ID_MERCHANT_CITY, ascii(merchantCity, MAX_CITY, true)))
                .append(field(ID_ADDITIONAL_DATA, field(SUB_REFERENCE_LABEL, NO_REFERENCE)));

        // The CRC covers its own identifier and length, so they go in first.
        payload.append(ID_CRC).append("04");
        return payload.append(crc16(payload.toString())).toString();
    }

    /**
     * Reads a pasted code. Rejects it if the CRC does not match — a truncated
     * code that still parses would otherwise pay the wrong key or nobody at all.
     */
    public static Parsed parse(String payload) {
        String code = payload == null ? "" : payload.trim();

        if (code.length() < 8) {
            throw new InvalidBrCodeException("Code is too short to be a Pix code");
        }

        int crcStart = code.length() - 8;
        if (!code.startsWith(ID_CRC + "04", crcStart)) {
            throw new InvalidBrCodeException("Code does not end with a checksum");
        }
        if (!crc16(code.substring(0, code.length() - 4)).equalsIgnoreCase(code.substring(crcStart + 4))) {
            throw new InvalidBrCodeException("Checksum does not match — the code was altered or truncated");
        }

        Map<String, String> fields = readFields(code.substring(0, crcStart));
        Map<String, String> merchantAccount =
                readFields(fields.getOrDefault(ID_MERCHANT_ACCOUNT, ""));

        if (!PIX_GUI.equalsIgnoreCase(merchantAccount.get(SUB_GUI))) {
            throw new InvalidBrCodeException("Not a Pix code");
        }

        String key = merchantAccount.get(SUB_KEY);
        if (key == null || key.isBlank()) {
            throw new InvalidBrCodeException("Code carries no Pix key");
        }

        return new Parsed(
                key.trim(),
                amountOf(fields.get(ID_AMOUNT)),
                merchantAccount.getOrDefault(SUB_DESCRIPTION, ""),
                fields.getOrDefault(ID_MERCHANT_NAME, ""),
                fields.getOrDefault(ID_MERCHANT_CITY, ""));
    }

    private static BigDecimal amountOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(raw.trim());
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException e) {
            throw new InvalidBrCodeException("Amount in the code is not a number");
        }
    }

    private static Map<String, String> readFields(String source) {
        Map<String, String> fields = new LinkedHashMap<>();

        int cursor = 0;
        while (cursor + 4 <= source.length()) {
            String id = source.substring(cursor, cursor + 2);
            int length;
            try {
                length = Integer.parseInt(source.substring(cursor + 2, cursor + 4));
            } catch (NumberFormatException e) {
                throw new InvalidBrCodeException("Malformed field length in the code");
            }

            int valueStart = cursor + 4;
            int valueEnd = valueStart + length;
            if (valueEnd > source.length()) {
                throw new InvalidBrCodeException("A field in the code runs past its end");
            }

            fields.putIfAbsent(id, source.substring(valueStart, valueEnd));
            cursor = valueEnd;
        }

        return fields;
    }

    /** {@code ID} + two-digit length + value. */
    private static String field(String id, String value) {
        if (value.length() > MAX_FIELD) {
            throw new InvalidBrCodeException("Field " + id + " is too long to encode");
        }
        return id + String.format("%02d", value.length()) + value;
    }

    /**
     * The payload is ASCII only, so accents are folded rather than dropped —
     * "José" has to survive as "JOSE", not as "JOS".
     */
    private static String ascii(String value, int maxLength, boolean upperCase) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String folded = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (upperCase) {
            folded = folded.toUpperCase();
        }
        return folded.length() > maxLength ? folded.substring(0, maxLength).trim() : folded;
    }

    /** CRC-16/CCITT-FALSE: polynomial 0x1021, seed 0xFFFF, no final XOR. */
    static String crc16(String payload) {
        int crc = 0xFFFF;

        for (byte b : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }

        return String.format("%04X", crc);
    }
}
