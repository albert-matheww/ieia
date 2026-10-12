package com.ieia.emissionfactor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmissionFactorRequest(
        @NotNull UUID activityTypeId,

        @NotNull @Pattern(regexp = "CO2E|ENERGY|WATER|WASTE")
        String impactType,

        @NotBlank @Size(max = 24) String unitFrom,

        @NotNull @Pattern(regexp = "kgCO2e|MJ|litre|kg")
        String unitTo,

        @NotNull @PositiveOrZero BigDecimal value,

        @Size(max = 80) String region,

        @NotBlank @Size(max = 255) String source,

        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
