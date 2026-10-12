package com.ieia.organization;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.dto.PageResponse;
import com.ieia.organization.dto.OrganizationDto;
import com.ieia.organization.dto.UpdateOrganizationRequest;
import com.ieia.security.SecurityUtils;
import com.ieia.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
@PreAuthorize("hasAnyRole('ORG_OWNER','ADMIN')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ApiResponse<OrganizationDto> get() {
        return ApiResponse.ok(organizationService.get(SecurityUtils.requirePrincipal().organizationId()));
    }

    @PutMapping
    public ApiResponse<OrganizationDto> update(@Valid @RequestBody UpdateOrganizationRequest req) {
        return ApiResponse.ok(
                organizationService.update(SecurityUtils.requirePrincipal().organizationId(), req));
    }

    @GetMapping("/members")
    public ApiResponse<PageResponse<UserProfileDto>> members(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(
                organizationService.members(SecurityUtils.requirePrincipal().organizationId(), pageable));
    }
}
