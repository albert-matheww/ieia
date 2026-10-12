package com.ieia.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code /api/auth/refresh} and {@code /api/auth/logout}. */
public record TokenRequest(
        @NotBlank String refreshToken
) {}
