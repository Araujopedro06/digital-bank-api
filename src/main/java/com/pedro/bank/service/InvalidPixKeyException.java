package com.pedro.bank.service;

public class InvalidPixKeyException extends RuntimeException {

    public InvalidPixKeyException(String message) {
        super(message);
    }
}
