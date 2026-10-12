package com.ieia.record.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkRecordRequest(
        @NotEmpty @Size(max = 500) @Valid List<RecordRequest> items
) {}
