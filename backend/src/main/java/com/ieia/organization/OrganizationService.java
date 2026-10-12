package com.ieia.organization;

import com.ieia.common.dto.PageResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.organization.dto.OrganizationDto;
import com.ieia.organization.dto.UpdateOrganizationRequest;
import com.ieia.user.UserRepository;
import com.ieia.user.dto.UserProfileDto;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(UUID organizationId) {
        return organizationRepository.findById(requireOrg(organizationId))
                .map(OrganizationDto::from)
                .orElseThrow(() -> ApiException.notFound("Organization"));
    }

    @Transactional
    public OrganizationDto update(UUID organizationId, UpdateOrganizationRequest req) {
        Organization org = organizationRepository.findById(requireOrg(organizationId))
                .orElseThrow(() -> ApiException.notFound("Organization"));
        if (req.name() != null && !req.name().isBlank()) org.setName(req.name().trim());
        if (req.industry() != null) org.setIndustry(blankToNull(req.industry()));
        if (req.country() != null) org.setCountry(blankToNull(req.country()));
        if (req.region() != null && !req.region().isBlank()) org.setRegion(req.region().trim());
        if (req.sizeCategory() != null) org.setSizeCategory(Organization.SizeCategory.valueOf(req.sizeCategory()));
        if (req.headcount() != null) org.setHeadcount(req.headcount());
        return OrganizationDto.from(org);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileDto> members(UUID organizationId, Pageable pageable) {
        return PageResponse.from(
                userRepository.findByOrganization_Id(requireOrg(organizationId), pageable),
                UserProfileDto::from);
    }

    private static UUID requireOrg(UUID organizationId) {
        if (organizationId == null) {
            throw ApiException.forbidden("Your account is not attached to an organization");
        }
        return organizationId;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
