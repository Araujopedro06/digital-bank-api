package com.pedro.bank.service;

public class LoanTermsNotOfferedException extends RuntimeException {

    public LoanTermsNotOfferedException(String field) {
        super("Loan terms not offered: " + field);
    }
}
