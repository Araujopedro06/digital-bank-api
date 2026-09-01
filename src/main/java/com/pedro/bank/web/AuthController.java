package com.pedro.bank.web;

import com.pedro.bank.service.AuthService;
import com.pedro.bank.web.dto.AuthResponse;
import com.pedro.bank.web.dto.FaceLoginRequest;
import com.pedro.bank.web.dto.LoginRequest;
import com.pedro.bank.web.dto.LoginResponse;
import com.pedro.bank.web.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a user and open their checking account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Check the password; returns a JWT, or a face challenge if one is enrolled")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/face")
    @Operation(summary = "Trade a face challenge token plus a matching face for a JWT")
    public AuthResponse loginWithFace(@Valid @RequestBody FaceLoginRequest request) {
        return authService.completeFaceLogin(request);
    }
}
