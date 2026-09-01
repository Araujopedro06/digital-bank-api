package com.pedro.bank.web.dto;

public record StepUpTokenResponse(String verificationToken, long expiresIn) {
}
