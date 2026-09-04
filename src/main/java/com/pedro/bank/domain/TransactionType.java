package com.pedro.bank.domain;

public enum TransactionType {
    DEPOSIT,
    TRANSFER_IN,
    TRANSFER_OUT,
    PIX_IN,
    PIX_OUT,
    /** The demo's play money, handed over by a fictional rich aunt. */
    ALLOWANCE,
    LOAN_CREDIT,
    LOAN_PAYMENT
}
