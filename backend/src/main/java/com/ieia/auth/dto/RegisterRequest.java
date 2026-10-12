package com.ieia.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. {@code accountType = ORGANIZATION} additionally creates
 * the organisation and makes this user its {@code ORG_OWNER}
 * (docs/03-api-specification.md §2).
 */
public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 10, max = 100, message = "password must be 10-100 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "password must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "password must contain a digit")
        String password,

        @NotBlank @Size(min = 2, max = 160)
        String fullName,

        @NotBlank
        @Pattern(regexp = "INDIVIDUAL|ORGANIZATION", message = "accountType must be INDIVIDUAL or ORGANIZATION")
        String accountType,

        @Size(max = 160)
        String organizationName,

        @Size(max = 120) String industry,
        @Size(max = 80)  String country,
        @Size(max = 80)  String region,

        @Positive Integer headcount
) {
    public boolean isOrganization() {
        return "ORGANIZATION".equals(accountType);
    }
}
