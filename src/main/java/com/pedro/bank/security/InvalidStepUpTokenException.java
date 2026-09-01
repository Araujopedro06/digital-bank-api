package com.pedro.bank.security;

public class InvalidStepUpTokenException extends RuntimeException {

    public InvalidStepUpTokenException() {
        super("Step-up verification token is missing, expired or already used");
    }
}
