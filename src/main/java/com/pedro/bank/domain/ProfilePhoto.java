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

@Entity
@Table(name = "profile_photos")
public class ProfilePhoto {

    /** Kept in step with the upload limit enforced in ProfilePhotoService. */
    public static final long MAX_BYTES = 2 * 1024 * 1024;

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    /**
     * Plain {@code byte[]} with no declared length. Hibernate maps that to
     * VARBINARY, which is what both H2's and PostgreSQL's BYTEA report; @Lob or a
     * large explicit length would make it expect BLOB and fail schema validation.
     * The real size limit is the BYTEA column plus the check in
     * ProfilePhotoService — {@code length} here is only DDL metadata, and Flyway
     * owns the DDL.
     */
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProfilePhoto() {
    }

    public ProfilePhoto(User user, String contentType, byte[] data) {
        this.user = user;
        replaceWith(contentType, data);
    }

    public void replaceWith(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.updatedAt = Instant.now();
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
