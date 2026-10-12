package com.ieia.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.integration.llm.LlmClient;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.recommendation.dto.RecommendationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final String REPHRASE_SYSTEM = """
            REPHRASE_RECOMMENDATIONS
            You rewrite sustainability recommendation text so it reads naturally.
            Rules: keep every number exactly as given; do not add new numbers; return a JSON
            array of {"rank": <int>, "title": <string>, "rationale": <string>} only.
            """;

    private final RecommendationEngine engine;
    private final RecommendationRepository repository;
    private final MonthlyMetricRepository metricRepository;
    private final LlmClient llm;
    private final ObjectMapper objectMapper;

    public RecommendationService(RecommendationEngine engine,
                                 RecommendationRepository repository,
                                 MonthlyMetricRepository metricRepository,
                                 LlmClient llm,
                                 ObjectMapper objectMapper) {
        this.engine = engine;
        this.repository = repository;
        this.metricRepository = metricRepository;
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse get(UUID userId, LocalDate period) {
        LocalDate p = resolvePeriod(userId, period);
        List<Recommendation> recs = repository.findByUserIdAndPeriodMonthOrderByRank(userId, p);
        if (recs.isEmpty()) {
            return generate(userId, p, false);
        }
        return assemble(userId, p, recs);
    }

    @Transactional
    public RecommendationResponse generate(UUID userId, LocalDate requestedPeriod, boolean useLlm) {
        LocalDate period = resolvePeriod(userId, requestedPeriod);
        List<RecommendationEngine.Candidate> candidates = engine.generate(userId, period);

        repository.deleteByUserIdAndPeriodMonth(userId, period);
        repository.flush();

        boolean generic = candidates.stream().allMatch(c -> c.reductionCo2eKg() == 0);
        List<Recommendation> saved = new java.util.ArrayList<>();
        int rank = 1;
        for (RecommendationEngine.Candidate c : candidates) {
            Recommendation r = new Recommendation();
            r.setUserId(userId);
            r.setPeriodMonth(period);
            r.setRank(rank++);
            r.setCategory(c.category());
            r.setTitle(c.title());
            r.setRationale(c.rationale());
            r.setActionType(mapActionType(c.actionType()));
            r.setAssumedChange(c.assumedChange());
            r.setEstimatedReductionCo2eKg(BigDecimal.valueOf(c.reductionCo2eKg()).setScale(4, RoundingMode.HALF_UP));
            r.setEstimatedReductionPct(BigDecimal.valueOf(c.reductionPct()).setScale(2, RoundingMode.HALF_UP));
            r.setEstimatedOther(c.estimatedOther());
            r.setConfidence(c.confidence());
            r.setBasis(generic ? "GENERIC" : "DATA_DRIVEN");
            r.setSource("RULE_ENGINE");
            saved.add(repository.save(r));
        }

        if (useLlm && !generic && llm.isAvailable() && !saved.isEmpty()) {
            rephraseWithLlm(saved);
        }

        log.info("Generated {} recommendations for user {} {} (basis {})",
                saved.size(), userId, period, generic ? "GENERIC" : "DATA_DRIVEN");
        return assemble(userId, period, saved);
    }

    @Transactional
    public RecommendationResponse.Item updateStatus(UUID userId, UUID id, String status) {
        Recommendation r = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recommendation"));
        String s = status == null ? "" : status.toUpperCase();
        if (!List.of("SUGGESTED", "ACCEPTED", "IN_PROGRESS", "DONE", "DISMISSED").contains(s)) {
            throw ApiException.validation("invalid status");
        }
        r.setStatus(s);
        return RecommendationResponse.Item.from(r);
    }

    // ---- LLM rephrasing (numbers frozen + re-verified) -----------------

    private void rephraseWithLlm(List<Recommendation> recs) {
        try {
            var items = objectMapper.createArrayNode();
            for (Recommendation r : recs) {
                var n = objectMapper.createObjectNode();
                n.put("rank", r.getRank());
                n.put("title", r.getTitle());
                n.put("rationale", r.getRationale());
                items.add(n);
            }
            String contextJson = objectMapper.writeValueAsString(Map.of("items", items));
            LlmClient.Response res = llm.complete(new LlmClient.Request(
                    REPHRASE_SYSTEM, List.of(),
                    "Rewrite these recommendations. Return only the JSON array.",
                    contextJson, 700));

            JsonNode arr = objectMapper.readTree(res.text());
            if (!arr.isArray()) return;
            Map<Integer, Recommendation> byRank = new java.util.HashMap<>();
            recs.forEach(r -> byRank.put(r.getRank(), r));

            int applied = 0;
            for (JsonNode node : arr) {
                Recommendation r = byRank.get(node.path("rank").asInt(-1));
                if (r == null) continue;
                String newTitle = node.path("title").asText(r.getTitle());
                String newRationale = node.path("rationale").asText(r.getRationale());
                if (numbersPreserved(r.getTitle(), newTitle) && numbersPreserved(r.getRationale(), newRationale)) {
                    r.setTitle(newTitle);
                    r.setRationale(newRationale);
                    r.setSource("LLM_ASSISTED");
                    applied++;
                }
            }
            log.info("LLM rephrased {}/{} recommendations ({})", applied, recs.size(), llm.providerName());
        } catch (Exception e) {
            log.warn("LLM rephrasing skipped: {}", e.getMessage());
        }
    }

    /** True when the rewritten text introduces no numeric token absent from the original. */
    private static boolean numbersPreserved(String original, String rewritten) {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        Matcher m = NUMBER.matcher(original == null ? "" : original);
        while (m.find()) allowed.add(m.group());
        Matcher r = NUMBER.matcher(rewritten == null ? "" : rewritten);
        while (r.find()) {
            if (!allowed.contains(r.group())) return false;
        }
        return true;
    }

    // ---- helpers -----------------------------------------------------

    private RecommendationResponse assemble(UUID userId, LocalDate period, List<Recommendation> recs) {
        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        RecommendationResponse.TopContributor top = null;
        if (metric != null && !metric.getCo2eByCategory().isEmpty()) {
            double total = metric.getCo2eByCategory().values().stream().mapToDouble(BigDecimal::doubleValue).sum();
            var max = metric.getCo2eByCategory().entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(null);
            if (max != null && total > 0) {
                top = new RecommendationResponse.TopContributor(max.getKey(),
                        BigDecimal.valueOf(max.getValue().doubleValue() / total * 100.0)
                                .setScale(2, RoundingMode.HALF_UP));
            }
        }
        String basis = recs.isEmpty() ? "GENERIC" : recs.get(0).getBasis();
        String source = recs.stream().anyMatch(r -> "LLM_ASSISTED".equals(r.getSource()))
                ? "LLM_ASSISTED" : "RULE_ENGINE";
        java.time.Instant generatedAt = recs.isEmpty() ? java.time.Instant.now() : recs.get(0).getGeneratedAt();
        return RecommendationResponse.of(PeriodUtil.format(period), generatedAt, basis, source, top, recs);
    }

    private LocalDate resolvePeriod(UUID userId, LocalDate requested) {
        if (requested != null) return requested;
        return metricRepository.findFirstByUserIdOrderByPeriodMonthDesc(userId)
                .map(MonthlyMetric::getPeriodMonth)
                .orElseThrow(() -> ApiException.notFound("No data yet — add some environmental records first"));
    }

    private static final java.util.Set<String> VALID_ACTIONS = java.util.Set.of(
            "REDUCE_CONSUMPTION", "SWITCH_SOURCE", "EFFICIENCY", "MODAL_SHIFT", "RECYCLING", "BEHAVIOURAL");

    private static String mapActionType(String change) {
        if (VALID_ACTIONS.contains(change)) return change;
        return switch (change) {
            case "PCT_REDUCTION", "PCT_INCREASE" -> "REDUCE_CONSUMPTION";
            case "RECYCLING_RATE" -> "RECYCLING";
            default -> "BEHAVIOURAL";
        };
    }
}
