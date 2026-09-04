package com.pedro.bank.service;

public class NoActiveLoanException extends RuntimeException {

    public NoActiveLoanException() {
        super("This account has no loan running");
    }
}
