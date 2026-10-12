package com.ieia.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,

        @NotBlank
        @Size(min = 10, max = 100, message = "password must be 10-100 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "password must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "password must contain a digit")
        String newPassword
) {}
