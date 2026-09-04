package com.pedro.bank.service;

public class PixChargeNotFoundException extends RuntimeException {

    public PixChargeNotFoundException(String id) {
        super("No live Pix charge with id " + id);
    }
}
