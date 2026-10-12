package com.ieia.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ActivityTypeRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9_]+", message = "code must be lower_snake_case")
        String code,

        @NotNull
        @Pattern(regexp = "ELECTRICITY|TRANSPORT|FUEL|WATER|WASTE|MATERIALS")
        String category,

        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 24) String defaultUnit,

        @NotEmpty List<@NotBlank @Size(max = 24) String> allowedUnits,

        Map<String, Object> attributeSchema,
        Boolean active
) {}
