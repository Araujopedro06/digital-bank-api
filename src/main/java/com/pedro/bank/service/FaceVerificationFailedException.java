package com.pedro.bank.service;

public class FaceVerificationFailedException extends RuntimeException {

    public FaceVerificationFailedException() {
        super("Face did not match the enrolled one");
    }
}
