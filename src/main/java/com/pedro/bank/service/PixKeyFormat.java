package com.pedro.bank.service;

import com.pedro.bank.domain.PixKeyType;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns what a user types into the single canonical form that gets stored, and
 * back into something readable for the screen.
 *
 * <p>Normalising before the uniqueness check is the whole point: without it
 * {@code 529.982.247-25} and {@code 52998224725} would be two different keys
 * pointing at two different accounts, and a payer would have no way to tell
 * which one they were about to pay.
 */
public final class PixKeyFormat {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    /** The longest key the BCB spec allows, which is an e-mail address. */
    public static final int MAX_LENGTH = 77;

    private PixKeyFormat() {
    }

    /**
     * @param ownerEmail the account owner's e-mail, used to refuse an e-mail key
     *                   that belongs to somebody else
     */
    public static String normalize(PixKeyType type, String raw, String ownerEmail) {
        String trimmed = raw == null ? "" : raw.trim();

        return switch (type) {
            case RANDOM -> UUID.randomUUID().toString();
            case CPF -> normalizeCpf(trimmed);
            case PHONE -> normalizePhone(trimmed);
            case EMAIL -> normalizeEmail(trimmed, ownerEmail);
        };
    }

    private static String normalizeCpf(String raw) {
        String digits = NON_DIGITS.matcher(raw).replaceAll("");
        if (!isValidCpf(digits)) {
            throw new InvalidPixKeyException("CPF is not valid");
        }
        return digits;
    }

    /**
     * Accepts what people actually type — {@code (11) 99999-8888},
     * {@code 11999998888}, {@code +55 11 99999-8888} — and stores the
     * {@code +55DDNNNNNNNNN} form the spec asks for.
     */
    private static String normalizePhone(String raw) {
        String digits = NON_DIGITS.matcher(raw).replaceAll("");

        if (digits.length() == 12 || digits.length() == 13) {
            if (!digits.startsWith("55")) {
                throw new InvalidPixKeyException("Only Brazilian phone numbers are accepted");
            }
            digits = digits.substring(2);
        }

        if (digits.length() != 10 && digits.length() != 11) {
            throw new InvalidPixKeyException("Phone number must have DDD and 8 or 9 digits");
        }
        if (digits.charAt(0) == '0') {
            throw new InvalidPixKeyException("Phone number must have DDD and 8 or 9 digits");
        }

        return "+55" + digits;
    }

    /**
     * An e-mail key must be the address the account was opened with. It is the
     * only kind of ownership this app can actually verify: a CPF or a phone
     * number here is self-declared, because confirming either one means a
     * document check or an SMS that a portfolio demo has no way to do.
     */
    private static String normalizeEmail(String raw, String ownerEmail) {
        String email = raw.toLowerCase();

        if (!EMAIL.matcher(email).matches() || email.length() > MAX_LENGTH) {
            throw new InvalidPixKeyException("E-mail is not valid");
        }
        if (!email.equals(ownerEmail.toLowerCase())) {
            throw new InvalidPixKeyException("An e-mail key must be the account's own e-mail");
        }

        return email;
    }

    /** The standard mod-11 check digits, plus the repeated-digit CPFs they let through. */
    public static boolean isValidCpf(String digits) {
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }

        for (int checkDigit = 0; checkDigit < 2; checkDigit++) {
            int length = 9 + checkDigit;
            int sum = 0;
            for (int i = 0; i < length; i++) {
                sum += (digits.charAt(i) - '0') * (length + 1 - i);
            }

            int remainder = sum % 11;
            int expected = remainder < 2 ? 0 : 11 - remainder;
            if (digits.charAt(length) - '0' != expected) {
                return false;
            }
        }

        return true;
    }

    /** How the key is written on screen. The stored value is never reformatted. */
    public static String display(PixKeyType type, String value) {
        return switch (type) {
            case CPF -> value.substring(0, 3) + "." + value.substring(3, 6) + "."
                    + value.substring(6, 9) + "-" + value.substring(9);
            case PHONE -> "(" + value.substring(3, 5) + ") " + value.substring(5, value.length() - 4)
                    + "-" + value.substring(value.length() - 4);
            case EMAIL, RANDOM -> value;
        };
    }

    /**
     * Guesses the type of a key someone pasted, so the payer does not have to
     * say which kind it is.
     */
    public static PixKeyType detectType(String raw) {
        String trimmed = raw == null ? "" : raw.trim();

        if (trimmed.contains("@")) {
            return PixKeyType.EMAIL;
        }
        if (trimmed.length() == 36 && trimmed.contains("-")) {
            return PixKeyType.RANDOM;
        }

        String digits = NON_DIGITS.matcher(trimmed).replaceAll("");
        if (digits.length() == 11 && isValidCpf(digits)) {
            return PixKeyType.CPF;
        }
        if (digits.length() >= 10) {
            return PixKeyType.PHONE;
        }

        throw new InvalidPixKeyException("Unrecognised Pix key");
    }

    /**
     * The canonical form of a key being *paid*, which unlike registration has no
     * owner to check an e-mail against.
     */
    public static String normalizeForLookup(String raw) {
        PixKeyType type = detectType(raw);
        String trimmed = raw.trim();

        return switch (type) {
            case EMAIL -> trimmed.toLowerCase();
            case RANDOM -> trimmed.toLowerCase();
            case CPF -> NON_DIGITS.matcher(trimmed).replaceAll("");
            case PHONE -> normalizePhone(trimmed);
        };
    }
}
