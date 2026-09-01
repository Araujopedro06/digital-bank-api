package com.pedro.bank.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceDescriptorRequest(
        @NotNull @Size(min = 128, max = 128) double[] descriptor) {
}
