package com.ieia.record.dto;

import java.util.List;

public record BulkRecordResult(
        int created,
        int failed,
        List<RecordResponse> records,
        List<FailedItem> failures
) {
    public record FailedItem(int index, String reason) {}
}
