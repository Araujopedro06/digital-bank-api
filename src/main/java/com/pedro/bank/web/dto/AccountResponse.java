package com.pedro.bank.web.dto;

import com.pedro.bank.domain.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String number, BigDecimal balance,
                              String ownerName, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getNumber(),
                account.getBalance(),
                account.getOwner().getName(),
                account.getCreatedAt());
    }
}
