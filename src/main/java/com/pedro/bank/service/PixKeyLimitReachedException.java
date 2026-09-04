package com.pedro.bank.service;

public class PixKeyLimitReachedException extends RuntimeException {

    public PixKeyLimitReachedException(int limit) {
        super("An account may hold at most " + limit + " Pix keys");
    }
}
