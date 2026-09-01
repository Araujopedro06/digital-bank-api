package com.pedro.bank.domain;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal requested) {
        super("Account %s has a balance of %s, which does not cover %s"
                .formatted(accountNumber, balance, requested));
    }
}
