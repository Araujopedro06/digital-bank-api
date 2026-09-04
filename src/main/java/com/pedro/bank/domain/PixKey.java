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

import java.time.Instant;
import java.util.UUID;

/**
 * An alias pointing at an account. The stored value is already normalised —
 * digits only for a CPF, {@code +55} form for a phone, lower case for an e-mail
 * — so the unique index is what actually enforces "one owner per key".
 */
@Entity
@Table(name = "pix_keys")
public class PixKey {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PixKeyType type;

    @Column(name = "key_value", nullable = false, unique = true, length = 77)
    private String value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PixKey() {
    }

    public PixKey(Account account, PixKeyType type, String value) {
        this.account = account;
        this.type = type;
        this.value = value;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public PixKeyType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
