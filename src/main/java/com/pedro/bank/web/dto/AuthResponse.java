package com.pedro.bank.web.dto;

public record AuthResponse(String token, String tokenType, long expiresIn, String name) {

    public static AuthResponse bearer(String token, long expiresIn, String name) {
        return new AuthResponse(token, "Bearer", expiresIn, name);
    }
}
