package com.ieia.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieia.common.dto.PageResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.scenario.dto.ScenarioRequest;
import com.ieia.scenario.dto.ScenarioResult;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScenarioService {

    private final ScenarioEngine engine;
    private final ScenarioRepository repository;
    private final ObjectMapper objectMapper;

    public ScenarioService(ScenarioEngine engine, ScenarioRepository repository, ObjectMapper objectMapper) {
        this.engine = engine;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ScenarioResult simulate(UUID userId, ScenarioRequest req) {
        return engine.simulate(userId, req);
    }

    @Transactional
    public Map<String, Object> create(UUID userId, ScenarioRequest req) {
        ScenarioResult result = engine.simulate(userId, req);
        Scenario s = new Scenario();
        s.setUserId(userId);
        applyAndSave(s, req, result);
        return view(s, result);
    }

    @Transactional
    public Map<String, Object> update(UUID userId, UUID id, ScenarioRequest req) {
        Scenario s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Scenario"));
        ScenarioResult result = engine.simulate(userId, req);
        applyAndSave(s, req, result);
        return view(s, result);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID userId, UUID id) {
        Scenario s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Scenario"));
        return view(s, objectMapper.convertValue(s.getResult(), ScenarioResult.class));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID userId, Pageable pageable) {
        return PageResponse.from(repository.findByUserIdOrderByCreatedAtDesc(userId, pageable), this::summary);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Scenario s = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Scenario"));
        repository.delete(s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compare(UUID userId, List<UUID> ids) {
        List<Scenario> scenarios = repository.findByUserIdAndIdIn(userId, ids);
        if (scenarios.isEmpty()) {
            throw ApiException.notFound("Scenarios");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> baseline = null;
        for (Scenario s : scenarios) {
            ScenarioResult r = objectMapper.convertValue(s.getResult(), ScenarioResult.class);
            if (baseline == null) {
                baseline = objectMapper.convertValue(r.baseline(), Map.class);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("name", s.getName());
            row.put("projected", r.projected());
            row.put("deltas", r.deltas());
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baseline", baseline);
        out.put("scenarios", rows);
        return out;
    }

    // ---- helpers -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private void applyAndSave(Scenario s, ScenarioRequest req, ScenarioResult result) {
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setBasePeriodFrom(PeriodUtil.parse(req.basePeriodFrom()));
        s.setBasePeriodTo(PeriodUtil.parse(req.basePeriodTo()));
        s.setBaselineSnapshot(objectMapper.convertValue(result.baseline(), Map.class));
        s.setAdjustments(objectMapper.convertValue(req.adjustments(), List.class));
        s.setResult(objectMapper.convertValue(result, Map.class));
        repository.save(s);
    }

    private Map<String, Object> view(Scenario s, ScenarioResult result) {
        Map<String, Object> m = summary(s);
        m.put("result", result);
        m.put("adjustments", s.getAdjustments());
        return m;
    }

    private Map<String, Object> summary(Scenario s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("basePeriodFrom", PeriodUtil.format(s.getBasePeriodFrom()));
        m.put("basePeriodTo", PeriodUtil.format(s.getBasePeriodTo()));
        m.put("createdAt", s.getCreatedAt());
        m.put("updatedAt", s.getUpdatedAt());
        Object deltas = s.getResult().get("deltas");
        if (deltas != null) m.put("deltas", deltas);
        return m;
    }
}
