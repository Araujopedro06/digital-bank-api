package com.pedro.bank.web.dto;

import java.time.Instant;

public record FaceStatusResponse(boolean enrolled, Instant consentedAt) {
}
