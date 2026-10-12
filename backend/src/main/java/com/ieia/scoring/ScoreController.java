package com.ieia.scoring;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.scoring.dto.MethodologyResponse;
import com.ieia.scoring.dto.ScoreResponse;
import com.ieia.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreQueryService scoreQueryService;
    private final ScoringService scoringService;

    public ScoreController(ScoreQueryService scoreQueryService, ScoringService scoringService) {
        this.scoreQueryService = scoreQueryService;
        this.scoringService = scoringService;
    }

    @GetMapping
    public ApiResponse<List<ScoreResponse>> history(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate f = from == null ? null : PeriodUtil.parse(from);
        LocalDate t = to == null ? null : PeriodUtil.parse(to);
        return ApiResponse.ok(scoreQueryService.history(SecurityUtils.currentUserId(), f, t));
    }

    @GetMapping("/latest")
    public ApiResponse<ScoreResponse> latest() {
        return ApiResponse.ok(scoreQueryService.latest(SecurityUtils.currentUserId()));
    }

    @GetMapping("/methodology")
    public ApiResponse<MethodologyResponse> methodology() {
        return ApiResponse.ok(MethodologyResponse.from(scoringService.activeConfig()));
    }

    @GetMapping("/{period}")
    public ApiResponse<ScoreResponse> forPeriod(@PathVariable String period) {
        return ApiResponse.ok(
                scoreQueryService.forPeriod(SecurityUtils.currentUserId(), PeriodUtil.parse(period)));
    }

    @PostMapping("/recompute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recompute(
            @RequestBody(required = false) Map<String, String> body) {
        String from = body == null ? null : body.get("from");
        String to = body == null ? null : body.get("to");
        int months = scoreQueryService.recompute(
                SecurityUtils.currentUserId(),
                from == null ? null : PeriodUtil.parse(from),
                to == null ? null : PeriodUtil.parse(to));
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of("recomputedMonths", months)));
    }
}
