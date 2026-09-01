package com.pedro.bank.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tokens for the two step-up flows:
 *
 * <ul>
 *   <li>{@link Purpose#LOGIN} — handed out after the password check when the user
 *       has a face enrolled, and traded for a JWT once the face matches.</li>
 *   <li>{@link Purpose#TRANSFER} — handed out after a face match, and spent when
 *       the transfer is submitted.</li>
 * </ul>
 *
 * <p>Tokens live in memory, which is fine for a single instance. A real
 * deployment behind more than one node would move this to Redis.
 */
@Service
public class StepUpTokenService {

    public enum Purpose {
        LOGIN,
        TRANSFER
    }

    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Issued(String email, Purpose purpose, Instant expiresAt) {
    }

    private final Map<String, Issued> tokens = new ConcurrentHashMap<>();

    public String issue(String email, Purpose purpose) {
        evictExpired();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Issued(email, purpose, Instant.now().plus(LIFETIME)));
        return token;
    }

    /**
     * Spends the token and returns the e-mail it was issued for. The token is
     * removed whether or not it turns out to be valid, so a guessed or replayed
     * value cannot be retried.
     *
     * @throws InvalidStepUpTokenException if it is unknown, expired, or was
     *                                     issued for a different purpose
     */
    public String consume(String token, Purpose purpose) {
        Issued issued = token == null ? null : tokens.remove(token);
        if (issued == null || issued.purpose() != purpose
                || issued.expiresAt().isBefore(Instant.now())) {
            throw new InvalidStepUpTokenException();
        }
        return issued.email();
    }

    public long lifetimeSeconds() {
        return LIFETIME.toSeconds();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        Iterator<Issued> iterator = tokens.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
    }
}
