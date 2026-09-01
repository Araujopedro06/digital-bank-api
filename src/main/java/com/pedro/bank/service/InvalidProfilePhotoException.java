package com.pedro.bank.service;

public class InvalidProfilePhotoException extends RuntimeException {

    public InvalidProfilePhotoException(String message) {
        super(message);
    }
}
