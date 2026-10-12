package com.ieia.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 160) String fullName,
        @Size(max = 80) String country,
        @Size(max = 80) String region
) {}
