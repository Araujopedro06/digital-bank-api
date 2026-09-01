package com.pedro.bank.web.dto;

import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, TransactionType type, BigDecimal amount,
                                  BigDecimal balanceAfter, String description,
                                  String counterpartyNumber, Instant createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getCounterpartyNumber(),
                transaction.getCreatedAt());
    }
}
