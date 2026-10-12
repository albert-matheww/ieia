package com.ieia.prediction;

import com.ieia.common.dto.ApiResponse;
import com.ieia.prediction.dto.PredictionResponse;
import com.ieia.security.SecurityUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/predictions")
@Validated
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    public ApiResponse<PredictionResponse> get(
            @RequestParam(defaultValue = "CO2E") String metric,
            @RequestParam(defaultValue = "1") @Min(1) @Max(6) int horizon) {
        return ApiResponse.ok(predictionService.getOrCreate(
                SecurityUtils.currentUserId(), metric, horizon, false));
    }

    @PostMapping
    public ApiResponse<PredictionResponse> create(@RequestBody(required = false) Map<String, Object> body) {
        String metric = body == null ? "CO2E" : String.valueOf(body.getOrDefault("metric", "CO2E"));
        int horizon = body == null ? 1 : ((Number) body.getOrDefault("horizon", 1)).intValue();
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return ApiResponse.ok(predictionService.getOrCreate(
                SecurityUtils.currentUserId(), metric, Math.max(1, Math.min(6, horizon)), force));
    }

    @GetMapping("/history")
    public ApiResponse<List<PredictionResponse>> history(@RequestParam(defaultValue = "CO2E") String metric) {
        return ApiResponse.ok(predictionService.history(SecurityUtils.currentUserId(), metric));
    }
}
