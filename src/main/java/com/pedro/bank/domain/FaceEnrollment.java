package com.pedro.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's enrolled face, stored only as the 128-number descriptor produced in
 * the browser — the captured image never reaches the server.
 *
 * <p>Biometric data is sensitive personal data under LGPD art. 5, II, so the
 * moment consent was given is recorded with it and the whole row can be deleted
 * on request.
 */
@Entity
@Table(name = "face_enrollments")
public class FaceEnrollment {

    /** face-api.js descriptors are always this long; anything else is not one. */
    public static final int DESCRIPTOR_LENGTH = 128;

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 4000)
    private String descriptor;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FaceEnrollment() {
    }

    public FaceEnrollment(User user, String descriptor) {
        this.user = user;
        this.descriptor = descriptor;
        this.consentedAt = Instant.now();
        this.updatedAt = this.consentedAt;
    }

    public void replaceWith(String descriptor) {
        this.descriptor = descriptor;
        this.updatedAt = Instant.now();
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
