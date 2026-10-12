package com.ieia.anomaly;

import com.ieia.anomaly.dto.AnomalyResponse;
import com.ieia.anomaly.dto.ScanResult;
import com.ieia.common.dto.ApiResponse;
import com.ieia.common.dto.PageResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AnomalyResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(anomalyService.list(
                SecurityUtils.currentUserId(), status,
                from == null ? null : PeriodUtil.parse(from),
                to == null ? null : PeriodUtil.parse(to),
                metric, severity, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnomalyResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(anomalyService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<ScanResult>> scan(@RequestBody(required = false) Map<String, String> body) {
        String from = body == null ? null : body.get("from");
        String to = body == null ? null : body.get("to");
        String sensitivity = body == null ? null : body.get("sensitivity");
        ScanResult result = anomalyService.scan(
                SecurityUtils.currentUserId(),
                from == null ? null : PeriodUtil.parse(from),
                to == null ? null : PeriodUtil.parse(to),
                sensitivity);
        return ResponseEntity.accepted().body(ApiResponse.ok(result));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AnomalyResponse> review(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(anomalyService.review(
                SecurityUtils.currentUserId(), id, body.get("status"), body.get("note")));
    }
}
