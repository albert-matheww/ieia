package com.ieia.activity;

import com.ieia.activity.dto.ActivityTypeDto;
import com.ieia.activity.dto.ActivityTypeRequest;
import com.ieia.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/activity-types")
public class ActivityTypeController {

    private final ActivityTypeService service;

    public ActivityTypeController(ActivityTypeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<java.util.List<ActivityTypeDto>> list(
            @RequestParam(required = false) ActivityCategory category,
            @RequestParam(name = "active", defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.list(category, activeOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityTypeDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ActivityTypeDto>> create(@Valid @RequestBody ActivityTypeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityTypeDto> update(@PathVariable UUID id, @Valid @RequestBody ActivityTypeRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActivityTypeDto> setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return ApiResponse.ok(service.setActive(id, Boolean.TRUE.equals(body.get("active"))));
    }
}
