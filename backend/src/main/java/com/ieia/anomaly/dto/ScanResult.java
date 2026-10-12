package com.ieia.anomaly.dto;

import java.util.List;
import java.util.Map;

public record ScanResult(
        int detected,
        int newlyOpened,
        int updated,
        List<AnomalyResponse> anomalies,
        Map<String, Object> mlSummary
) {}
