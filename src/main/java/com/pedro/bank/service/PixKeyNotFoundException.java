package com.pedro.bank.service;

public class PixKeyNotFoundException extends RuntimeException {

    public PixKeyNotFoundException(String key) {
        super("No account holds the Pix key " + key);
    }
}
