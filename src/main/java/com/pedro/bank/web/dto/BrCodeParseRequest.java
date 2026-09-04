package com.pedro.bank.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrCodeParseRequest(@NotBlank @Size(max = 1024) String payload) {
}
