package com.pedro.bank.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceEnrollmentRequest(
        @NotNull @Size(min = 128, max = 128) double[] descriptor,
        /** LGPD art. 11: biometric data needs specific, highlighted consent. */
        @AssertTrue(message = "Consent is required to store biometric data") boolean consent) {
}
