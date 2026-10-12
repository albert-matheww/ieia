package com.ieia.organization.dto;

import com.ieia.organization.Organization;

import java.util.UUID;

public record OrganizationSummaryDto(
        UUID id,
        String name,
        String sizeCategory,
        String region,
        Integer headcount
) {
    public static OrganizationSummaryDto from(Organization org) {
        if (org == null) return null;
        return new OrganizationSummaryDto(
                org.getId(),
                org.getName(),
                org.getSizeCategory().name(),
                org.getRegion(),
                org.getHeadcount()
        );
    }
}
