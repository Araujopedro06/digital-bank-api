package com.pedro.bank.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceLoginRequest(
        @NotBlank String challengeToken,
        @NotNull @Size(min = 128, max = 128) double[] descriptor) {
}
