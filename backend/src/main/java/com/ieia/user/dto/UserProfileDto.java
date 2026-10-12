package com.ieia.user.dto;

import com.ieia.organization.dto.OrganizationSummaryDto;
import com.ieia.user.User;

import java.time.Instant;
import java.util.UUID;

/** Safe representation of a user — never carries the password hash. */
public record UserProfileDto(
        UUID id,
        String email,
        String fullName,
        String role,
        boolean active,
        Instant lastLoginAt,
        OrganizationSummaryDto organization
) {
    public static UserProfileDto from(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.getLastLoginAt(),
                OrganizationSummaryDto.from(user.getOrganization())
        );
    }
}
