package com.pedro.bank.config;

import com.pedro.bank.repository.UserRepository;
import com.pedro.bank.service.AccountService;
import com.pedro.bank.service.AuthService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

/**
 * Gives the dev profile two accounts to transfer between, so the UI has
 * something to show on first run.
 */
@Configuration
@Profile("dev")
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
