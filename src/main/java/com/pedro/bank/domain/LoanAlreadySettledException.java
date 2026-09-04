package com.pedro.bank.domain;

public class LoanAlreadySettledException extends RuntimeException {

    public LoanAlreadySettledException() {
        super("This loan is already paid off");
    }
}
