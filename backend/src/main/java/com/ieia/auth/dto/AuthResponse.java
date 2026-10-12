package com.ieia.auth.dto;

import com.ieia.user.dto.UserProfileDto;

/** Issued on register / login / refresh (docs/03-api-specification.md §2). */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        UserProfileDto user
) {
    public static AuthResponse of(String accessToken, long expiresIn, String refreshToken, UserProfileDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
    }
}
