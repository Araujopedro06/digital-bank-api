package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.User;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.repository.UserRepository;
import com.pedro.bank.security.JwtService;
import com.pedro.bank.web.dto.AuthResponse;
import com.pedro.bank.web.dto.LoginRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = userRepository.save(
                new User(request.name(), email, passwordEncoder.encode(request.password())));

        // Every user gets exactly one checking account at sign-up.
        accountRepository.save(new Account(user, generateAccountNumber(), BigDecimal.ZERO));

        return AuthResponse.bearer(jwtService.generate(email), jwtService.expiresInSeconds(), user.getName());
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email).orElseThrow();
        return AuthResponse.bearer(jwtService.generate(email), jwtService.expiresInSeconds(), user.getName());
    }

    private String generateAccountNumber() {
        String number;
        do {
            number = String.valueOf(100_000_000L + (long) (RANDOM.nextDouble() * 899_999_999L));
        } while (accountRepository.existsByNumber(number));
        return number;
    }
}
