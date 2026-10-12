package com.ieia.metrics;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.DashboardResponse;
import com.ieia.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard(@RequestParam(required = false) String period) {
        LocalDate p = period == null ? null : PeriodUtil.parse(period);
        return ApiResponse.ok(dashboardService.dashboard(SecurityUtils.currentUserId(), p));
    }
}
