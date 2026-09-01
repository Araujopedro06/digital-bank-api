package com.pedro.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ledger line, always from the point of view of {@code account}. A transfer
 * writes two of these: TRANSFER_OUT on the sender, TRANSFER_IN on the receiver.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(name = "counterparty_number", length = 12)
    private String counterpartyNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Transaction() {
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount,
                       String description, String counterpartyNumber) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.description = description;
        this.counterpartyNumber = counterpartyNumber;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getCounterpartyNumber() {
        return counterpartyNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
