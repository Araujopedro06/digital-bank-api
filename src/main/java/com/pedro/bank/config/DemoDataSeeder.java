package com.pedro.bank.config;

import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.repository.UserRepository;
import com.pedro.bank.service.AccountService;
import com.pedro.bank.service.AuthService;
import com.pedro.bank.service.PixKeyService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Gives a fresh database two accounts to transfer between, so the UI has
 * something to show on first run and a visitor to the public demo has somewhere
 * to log in. Driven by a setting rather than the profile, because the deployed
 * demo runs the prod profile and still wants the accounts.
 */
@Configuration
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String PEDRO = "pedro@demo.com";
    private static final String MARIA = "maria@demo.com";

    @Bean
    CommandLineRunner seedDemoUsers(UserRepository userRepository, AuthService authService,
                                    AccountService accountService, PixKeyService pixKeyService) {
        return args -> {
            if (userRepository.count() == 0) {
                authService.register(new RegisterRequest("Pedro Araujo", PEDRO, "demo1234"));
                accountService.deposit(PEDRO,
                        new DepositRequest(new BigDecimal("2500.00"), "Opening balance"));

                authService.register(new RegisterRequest("Maria Silva", MARIA, "demo1234"));
                accountService.deposit(MARIA,
                        new DepositRequest(new BigDecimal("800.00"), "Opening balance"));
            }

            // Keys are ensured on every start rather than only on a fresh database.
            // A deployment that has been running since before Pix existed already
            // has these users and none of their keys — which is the one state
            // where a visitor opens the Pix screen and finds nobody to pay.
            //
            // A key on each side, and two different kinds, so the screen shows
            // what a key list actually looks like.
            ensureKey(pixKeyService, userRepository, PEDRO, PixKeyType.EMAIL, PEDRO);
            ensureKey(pixKeyService, userRepository, PEDRO, PixKeyType.RANDOM, null);
            ensureKey(pixKeyService, userRepository, MARIA, PixKeyType.EMAIL, MARIA);
            ensureKey(pixKeyService, userRepository, MARIA, PixKeyType.PHONE, "(11) 98765-4321");
        };
    }

    /**
     * Registers a key only if that account has none of that kind, so restarting
     * the app does not keep adding keys until it hits the per-account limit.
     */
    private void ensureKey(PixKeyService pixKeyService, UserRepository userRepository,
                           String email, PixKeyType type, String value) {
        if (!userRepository.existsByEmail(email)) {
            return;
        }

        boolean alreadyHasOne = pixKeyService.list(email).stream()
                .anyMatch(key -> key.getType() == type);
        if (alreadyHasOne) {
            return;
        }

        // A key someone else already claimed is not worth failing startup over —
        // on a shared demo, a visitor may well have registered this phone number.
        try {
            pixKeyService.register(email, type, value);
        } catch (RuntimeException e) {
            log.warn("Could not seed the {} Pix key for {}: {}", type, email, e.getMessage());
        }
    }
}
