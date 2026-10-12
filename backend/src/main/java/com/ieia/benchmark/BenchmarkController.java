package com.ieia.benchmark;

import com.ieia.benchmark.dto.BenchmarkComparison;
import com.ieia.common.dto.ApiResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final BenchmarkingService service;
    private final BenchmarkRepository repository;

    public BenchmarkController(BenchmarkingService service, BenchmarkRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<Benchmark>> activeSet() {
        return ApiResponse.ok(service.activeSet(SecurityUtils.currentUserId()));
    }

    @GetMapping("/comparison")
    public ApiResponse<BenchmarkComparison> comparison(@RequestParam(required = false) String period) {
        LocalDate p = period == null ? null : PeriodUtil.parse(period);
        return ApiResponse.ok(service.comparison(SecurityUtils.currentUserId(), p));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Benchmark> upsert(@RequestBody Map<String, Object> body) {
        String scope = str(body, "scope");
        String segment = str(body, "segment");
        String metric = str(body, "metric");
        String periodKind = body.getOrDefault("period", "MONTH").toString();
        String region = body.getOrDefault("region", "GLOBAL").toString();

        Benchmark b = repository
                .findFirstByScopeAndSegmentAndMetricAndPeriodAndRegion(scope, segment, metric, periodKind, region)
                .orElseGet(Benchmark::new);
        b.setScope(scope);
        b.setSegment(segment);
        b.setMetric(metric);
        b.setPeriod(periodKind);
        b.setRegion(region);
        b.setUnit(str(body, "unit"));
        b.setValue(new BigDecimal(body.get("value").toString()));
        b.setSource(body.getOrDefault("source", "configured").toString());
        b.setOfficial(Boolean.TRUE.equals(body.get("isOfficial")));
        if (body.get("ratingBands") instanceof Map<?, ?> rb) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) rb;
            b.setRatingBands(cast);
        }
        return ApiResponse.ok(repository.save(b));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw ApiException.validation(key + " is required");
        }
        return v.toString();
    }
}
