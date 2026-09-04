package com.pedro.bank.service;

public class InvalidBrCodeException extends RuntimeException {

    public InvalidBrCodeException(String message) {
        super(message);
    }
}
