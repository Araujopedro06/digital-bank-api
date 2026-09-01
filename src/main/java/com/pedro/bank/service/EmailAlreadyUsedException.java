package com.pedro.bank.service;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("E-mail " + email + " is already registered");
    }
}
