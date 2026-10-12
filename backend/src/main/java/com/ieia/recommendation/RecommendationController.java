package com.ieia.recommendation;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.recommendation.dto.RecommendationResponse;
import com.ieia.security.SecurityUtils;
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
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> get(@RequestParam(required = false) String period) {
        LocalDate p = period == null ? null : PeriodUtil.parse(period);
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId(), p));
    }

    @PostMapping("/generate")
    public ApiResponse<RecommendationResponse> generate(@RequestBody(required = false) Map<String, Object> body) {
        String period = body == null ? null : (String) body.get("period");
        boolean useLlm = body != null && Boolean.TRUE.equals(body.get("useLlm"));
        return ApiResponse.ok(service.generate(
                SecurityUtils.currentUserId(),
                period == null ? null : PeriodUtil.parse(period),
                useLlm));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RecommendationResponse.Item> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.updateStatus(SecurityUtils.currentUserId(), id, body.get("status")));
    }
}
