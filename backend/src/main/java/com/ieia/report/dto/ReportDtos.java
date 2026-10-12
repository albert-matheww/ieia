package com.ieia.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {}

    public record CreateReportRequest(
            @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String from,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String to,
            @Pattern(regexp = "JSON|PDF") String format,
            List<String> include
    ) {}

    public record ReportSummary(
            UUID id,
            String title,
            String periodFrom,
            String periodTo,
            String format,
            String status,
            Long fileSizeBytes,
            Instant generatedAt,
            Instant createdAt
    ) {}

    public record ReportDetail(
            ReportSummary meta,
            Map<String, Object> payload
    ) {}
}
