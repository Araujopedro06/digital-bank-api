package com.pedro.bank.service;

public class LoanAlreadyActiveException extends RuntimeException {

    public LoanAlreadyActiveException() {
        super("This account already has a loan running");
    }
}
