package com.pedro.bank.service;

public class FaceNotEnrolledException extends RuntimeException {

    public FaceNotEnrolledException() {
        super("This user has no enrolled face");
    }
}
