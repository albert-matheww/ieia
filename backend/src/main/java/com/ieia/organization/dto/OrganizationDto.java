package com.ieia.organization.dto;

import com.ieia.organization.Organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(
        UUID id,
        String name,
        String industry,
        String country,
        String region,
        String sizeCategory,
        Integer headcount,
        Instant createdAt
) {
    public static OrganizationDto from(Organization org) {
        return new OrganizationDto(
                org.getId(), org.getName(), org.getIndustry(), org.getCountry(),
                org.getRegion(), org.getSizeCategory().name(), org.getHeadcount(), org.getCreatedAt());
    }
}
