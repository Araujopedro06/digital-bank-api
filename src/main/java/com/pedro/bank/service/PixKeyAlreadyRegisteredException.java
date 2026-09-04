package com.pedro.bank.service;

public class PixKeyAlreadyRegisteredException extends RuntimeException {

    public PixKeyAlreadyRegisteredException() {
        super("This Pix key is already registered");
    }
}
