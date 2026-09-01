package com.pedro.bank.service;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String reference) {
        super("No account found for " + reference);
    }
}
