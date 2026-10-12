package com.ieia.record.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Create/update payload for an environmental record (docs/03-api-specification.md §5). */
public record RecordRequest(

        @NotNull UUID activityTypeId,

        @NotBlank
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "period must be YYYY-MM")
        String period,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
        BigDecimal amount,

        @NotBlank @Size(max = 24)
        String unit,

        @Size(max = 160) String location,
        @Size(max = 120) String sourceDetail,

        Map<String, Object> attributes,

        @Size(max = 4000) String note
) {}
