package com.ieia.metrics;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.ContributorsResponse;
import com.ieia.metrics.dto.ExplainResponse;
import com.ieia.metrics.dto.ImpactResponse;
import com.ieia.metrics.dto.TrendsResponse;
import com.ieia.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private final ImpactQueryService impactQueryService;

    public ImpactController(ImpactQueryService impactQueryService) {
        this.impactQueryService = impactQueryService;
    }

    @GetMapping
    public ApiResponse<ImpactResponse> impact(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(impactQueryService.impact(
                SecurityUtils.currentUserId(), parse(from), parse(to)));
    }

    @GetMapping("/trends")
    public ApiResponse<TrendsResponse> trends(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String metrics) {
        List<String> wanted = metrics == null || metrics.isBlank() ? List.of()
                : Arrays.stream(metrics.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ApiResponse.ok(impactQueryService.trends(
                SecurityUtils.currentUserId(), parse(from), parse(to), wanted));
    }

    @GetMapping("/contributors")
    public ApiResponse<ContributorsResponse> contributors(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "category") String by,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(impactQueryService.contributors(
                SecurityUtils.currentUserId(), parse(period), by, limit));
    }

    @GetMapping("/explain")
    public ApiResponse<ExplainResponse> explain(@RequestParam(required = false) String period) {
        return ApiResponse.ok(impactQueryService.explain(SecurityUtils.currentUserId(), parse(period)));
    }

    private static LocalDate parse(String period) {
        return period == null ? null : PeriodUtil.parse(period);
    }
}
