package com.ieia.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(min = 2, max = 160) String name,
        @Size(max = 120) String industry,
        @Size(max = 80) String country,
        @Size(max = 80) String region,
        @Pattern(regexp = "INDIVIDUAL|SMALL|MEDIUM|LARGE") String sizeCategory,
        @Positive Integer headcount
) {}
