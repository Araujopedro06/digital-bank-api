package com.pedro.bank.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sent in a body rather than a query string: a Pix key is a CPF, a phone number
 * or an e-mail address, and those have no business sitting in a URL that ends up
 * in access logs and browser history.
 */
public record PixLookupRequest(@NotBlank @Size(max = 77) String key) {
}
