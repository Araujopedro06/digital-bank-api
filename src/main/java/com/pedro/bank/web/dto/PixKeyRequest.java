package com.pedro.bank.web.dto;

import com.pedro.bank.domain.PixKeyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** For a RANDOM key the value is ignored — the bank issues it. */
public record PixKeyRequest(
        @NotNull PixKeyType type,
        @Size(max = 77) String value) {
}
