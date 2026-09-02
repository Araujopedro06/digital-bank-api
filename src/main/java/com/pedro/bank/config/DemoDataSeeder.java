package com.pedro.bank.config;

import com.pedro.bank.repository.UserRepository;
import com.pedro.bank.service.AccountService;
import com.pedro.bank.service.AuthService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
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

    @Bean
    CommandLineRunner seedDemoUsers(UserRepository userRepository, AuthService authService,
                                    AccountService accountService) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            authService.register(new RegisterRequest("Pedro Araujo", "pedro@demo.com", "demo1234"));
            accountService.deposit("pedro@demo.com",
                    new DepositRequest(new BigDecimal("2500.00"), "Opening balance"));

            authService.register(new RegisterRequest("Maria Silva", "maria@demo.com", "demo1234"));
            accountService.deposit("maria@demo.com",
                    new DepositRequest(new BigDecimal("800.00"), "Opening balance"));
        };
    }
}
