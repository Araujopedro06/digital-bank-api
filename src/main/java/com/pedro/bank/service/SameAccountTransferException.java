package com.pedro.bank.service;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("Cannot transfer to the same account");
    }
}
