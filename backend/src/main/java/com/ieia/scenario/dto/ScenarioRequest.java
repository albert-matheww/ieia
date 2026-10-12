package com.ieia.scenario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScenarioRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,

        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String basePeriodFrom,
        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String basePeriodTo,

        @NotEmpty @Size(max = 20) @Valid List<Adjustment> adjustments
) {
    public record Adjustment(
            @NotNull @Valid Target target,
            @NotNull
            @Pattern(regexp = "PCT_REDUCTION|PCT_INCREASE|MODAL_SHIFT|ABSOLUTE|RECYCLING_RATE")
            String change,
            @NotNull Double value,
            Params params
    ) {}

    public record Target(
            @NotNull @Pattern(regexp = "CATEGORY|ACTIVITY_TYPE") String type,
            @NotBlank String value
    ) {}

    public record Params(String toActivityCode, Double sharePct) {}
}
