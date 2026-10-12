package com.ieia.scenario;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.dto.PageResponse;
import com.ieia.scenario.dto.ScenarioRequest;
import com.ieia.scenario.dto.ScenarioResult;
import com.ieia.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @PostMapping("/simulate")
    public ApiResponse<ScenarioResult> simulate(@Valid @RequestBody ScenarioRequest req) {
        return ApiResponse.ok(scenarioService.simulate(SecurityUtils.currentUserId(), req));
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(scenarioService.list(SecurityUtils.currentUserId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable UUID id) {
        return ApiResponse.ok(scenarioService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody ScenarioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(scenarioService.create(SecurityUtils.currentUserId(), req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable UUID id, @Valid @RequestBody ScenarioRequest req) {
        return ApiResponse.ok(scenarioService.update(SecurityUtils.currentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        scenarioService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestBody Map<String, List<UUID>> body) {
        return ApiResponse.ok(scenarioService.compare(
                SecurityUtils.currentUserId(), body.getOrDefault("scenarioIds", List.of())));
    }
}
