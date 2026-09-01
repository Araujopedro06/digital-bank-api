package com.pedro.bank.service;

public class InvalidFaceDescriptorException extends RuntimeException {

    public InvalidFaceDescriptorException() {
        super("Face descriptor must be 128 finite numbers");
    }
}
