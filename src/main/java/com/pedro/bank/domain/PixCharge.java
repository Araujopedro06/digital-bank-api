package com.pedro.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A request to be paid that can be handed to someone — the target of a QR code
 * or a shared link.
 *
 * <p>It holds the key rather than the link holding it, so what travels in the
 * open is only this row's id.
 */
@Entity
@Table(name = "pix_charges")
public class PixCharge {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pix_key_id", nullable = false)
    private PixKey key;

    /** Null means the payer decides how much to send. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 120)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PixCharge() {
    }

    public PixCharge(PixKey key, BigDecimal amount, String description, Instant expiresAt) {
        this.key = key;
        this.amount = amount;
        this.description = description;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean hasExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public PixKey getKey() {
        return key;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
