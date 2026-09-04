package com.pedro.bank.service;

import java.time.Instant;

public class AllowanceTooSoonException extends RuntimeException {

    private final Instant availableAt;

    public AllowanceTooSoonException(Instant availableAt) {
        super("The aunt is not taking calls again until " + availableAt);
        this.availableAt = availableAt;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }
}
