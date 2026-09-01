package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.User;
import com.pedro.bank.repository.AccountRepository;
import com.pedro.bank.repository.UserRepository;
import com.pedro.bank.security.JwtService;
import com.pedro.bank.security.StepUpTokenService;
import com.pedro.bank.web.dto.AuthResponse;
import com.pedro.bank.web.dto.FaceLoginRequest;
import com.pedro.bank.web.dto.LoginRequest;
import com.pedro.bank.web.dto.LoginResponse;
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
    private final FaceRecognitionService faceRecognitionService;
    private final StepUpTokenService stepUpTokenService;

    public AuthService(UserRepository userRepository, AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService, FaceRecognitionService faceRecognitionService,
                       StepUpTokenService stepUpTokenService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.faceRecognitionService = faceRecognitionService;
        this.stepUpTokenService = stepUpTokenService;
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

    /**
     * Checks the password. A user who has enrolled a face gets a challenge token
     * rather than a JWT — {@link #completeFaceLogin} issues the JWT once the face
     * matches.
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email).orElseThrow();

        if (faceRecognitionService.isEnrolled(email)) {
            return LoginResponse.faceRequired(
                    stepUpTokenService.issue(email, StepUpTokenService.Purpose.LOGIN),
                    user.getName());
        }
        return LoginResponse.complete(tokenFor(user));
    }

    public AuthResponse completeFaceLogin(FaceLoginRequest request) {
        String email = stepUpTokenService.consume(
                request.challengeToken(), StepUpTokenService.Purpose.LOGIN);

        if (!faceRecognitionService.verify(email, request.descriptor()).matched()) {
            throw new FaceVerificationFailedException();
        }

        return tokenFor(userRepository.findByEmail(email).orElseThrow());
    }

    private AuthResponse tokenFor(User user) {
        return AuthResponse.bearer(jwtService.generate(user.getEmail()),
                jwtService.expiresInSeconds(), user.getName());
    }

    private String generateAccountNumber() {
        String number;
        do {
            number = String.valueOf(100_000_000L + (long) (RANDOM.nextDouble() * 899_999_999L));
        } while (accountRepository.existsByNumber(number));
        return number;
    }
}
