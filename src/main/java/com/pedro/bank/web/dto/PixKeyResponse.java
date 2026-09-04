package com.pedro.bank.web.dto;

import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.service.PixKeyFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * @param value   the canonical key, which is what gets pasted or encoded
 * @param display the same key punctuated for reading
 */
public record PixKeyResponse(UUID id, PixKeyType type, String value, String display,
                             Instant createdAt) {

    public static PixKeyResponse from(PixKey key) {
        return new PixKeyResponse(
                key.getId(),
                key.getType(),
                key.getValue(),
                PixKeyFormat.display(key.getType(), key.getValue()),
                key.getCreatedAt());
    }
}
