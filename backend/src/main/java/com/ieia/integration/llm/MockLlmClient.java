package com.ieia.integration.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, offline LLM stand-in — the default provider so IEIA runs with
 * no API key. It never invents numbers: every figure in its output is copied
 * straight from the supplied context JSON. Behaviour is a pure function of the
 * request, which is also what the unit tests assert against.
 */
@Component
@ConditionalOnProperty(name = "ieia.llm.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public MockLlmClient() {
        log.info("LLM provider: MOCK (deterministic, offline — no API key required)");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public Response complete(Request request) {
        JsonNode ctx = parse(request.contextJson());
        String text = (request.systemPrompt() != null && request.systemPrompt().contains("REPHRASE_RECOMMENDATIONS"))
                ? rephraseRecommendations(ctx)
                : answerQuestion(request.userPrompt() == null ? "" : request.userPrompt(), ctx);
        return new Response(text, "mock-analyst-v1", 0, estimateTokens(text), java.util.Map.of("provider", "MOCK"));
    }

    // ---- assistant mode ---------------------------------------------------

    private String answerQuestion(String question, JsonNode ctx) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> parts = new ArrayList<>();

        boolean asksWhyScore = (q.contains("why") || q.contains("reason")) && q.contains("score");
        boolean asksContributor = q.contains("contributor") || q.contains("causing") || q.contains("biggest")
                || q.contains("largest") || q.contains("most of my");
        boolean asksReduce = q.contains("reduce") || q.contains("lower") || q.contains("cut down") || q.contains("improve");
        boolean asksHighestMonth = q.contains("highest") && q.contains("month");
        boolean asksSummary = q.contains("summary") || q.contains("performance") || q.contains("how am i")
                || q.contains("overview");
        boolean asksPriority = q.contains("prioriti") || q.contains("should i focus") || q.contains("what should i");
        boolean asksWhatIf = q.contains("what if") || q.contains("what happens if");

        if (asksWhyScore && ctx.has("scoreDelta") && !ctx.path("scoreDelta").isNull()) {
            double d = ctx.path("scoreDelta").asDouble();
            String dir = d >= 0 ? "increased" : "decreased";
            StringBuilder sb = new StringBuilder("Your environmental score ").append(dir)
                    .append(" by ").append(fmt(Math.abs(d))).append(" points");
            if (ctx.has("period")) sb.append(" in ").append(ctx.path("period").asText());
            sb.append(". The main drivers were:");
            for (JsonNode drv : ctx.path("drivers")) {
                sb.append("\n• ").append(cap(drv.path("category").asText()))
                  .append(": metric changed ").append(fmt(drv.path("changePct").asDouble()))
                  .append("% (").append(drv.path("direction").asText().toLowerCase()).append(", ")
                  .append(fmt(drv.path("pointsImpact").asDouble())).append(" pts)");
            }
            parts.add(sb.toString());
        } else if (asksHighestMonth && ctx.has("metrics12mo")) {
            JsonNode best = null;
            for (JsonNode m : ctx.path("metrics12mo")) {
                if (best == null || m.path("co2eKg").asDouble() > best.path("co2eKg").asDouble()) best = m;
            }
            if (best != null) {
                parts.add("Your highest-emission month on record is " + best.path("period").asText()
                        + " at " + fmt(best.path("co2eKg").asDouble()) + " kg CO₂e.");
            }
        } else if (asksContributor && ctx.has("topContributors") && ctx.path("topContributors").size() > 0) {
            JsonNode top = ctx.path("topContributors").get(0);
            StringBuilder sb = new StringBuilder("Your largest contributor is ")
                    .append(top.path("category").asText().toLowerCase())
                    .append(", about ").append(fmt(top.path("pct").asDouble()))
                    .append("% of your total CO₂e");
            if (top.has("co2eKg")) sb.append(" (").append(fmt(top.path("co2eKg").asDouble())).append(" kg)");
            sb.append(". Full breakdown:");
            for (JsonNode c : ctx.path("topContributors")) {
                sb.append("\n• ").append(cap(c.path("category").asText())).append(" — ")
                  .append(fmt(c.path("pct").asDouble())).append("%");
            }
            parts.add(sb.toString());
        }

        if ((asksReduce || asksPriority) && ctx.has("recommendations") && ctx.path("recommendations").size() > 0) {
            StringBuilder sb = new StringBuilder(asksPriority
                    ? "Based on your data, prioritise:" : "Actions that would reduce your footprint the most:");
            int n = 0;
            for (JsonNode rec : ctx.path("recommendations")) {
                if (n++ >= 3) break;
                sb.append("\n").append(n).append(". ").append(rec.path("title").asText())
                  .append(" — est. ").append(fmt(rec.path("estimatedReductionCo2eKg").asDouble()))
                  .append(" kg CO₂e/month");
            }
            parts.add(sb.toString());
        } else if ((asksReduce || asksPriority) && ctx.has("topContributors")
                && ctx.path("topContributors").size() > 0) {
            parts.add("Start with your biggest contributor, "
                    + ctx.path("topContributors").get(0).path("category").asText().toLowerCase()
                    + ". Open the What-If Simulator to size specific changes against your own data.");
        }

        if (asksWhatIf) {
            parts.add("I can describe options, but for exact numbers use the What-If Simulator — it applies the "
                    + "change to your real baseline and the real emission factors.");
        }

        if (asksSummary || parts.isEmpty()) {
            parts.add(summarise(ctx));
        }

        StringBuilder out = new StringBuilder(String.join("\n\n", parts));
        if (ctx.has("dataWindow")) {
            out.append("\n\n(Figures are from your stored data for ").append(ctx.path("dataWindow").asText())
               .append(". I only use numbers you have recorded.)");
        }
        return out.toString();
    }

    private String summarise(JsonNode ctx) {
        StringBuilder sb = new StringBuilder("Summary of your environmental performance");
        if (ctx.has("period")) sb.append(" (").append(ctx.path("period").asText()).append(")");
        sb.append(":");
        if (ctx.has("score") && !ctx.path("score").isNull()) {
            sb.append("\n• Environmental score: ").append(fmt(ctx.path("score").asDouble())).append("/100");
            if (ctx.has("scoreDelta") && !ctx.path("scoreDelta").isNull()) {
                double d = ctx.path("scoreDelta").asDouble();
                sb.append(" (").append(d >= 0 ? "+" : "").append(fmt(d)).append(" vs previous month)");
            }
        }
        if (ctx.has("co2eKg")) sb.append("\n• CO₂e this period: ").append(fmt(ctx.path("co2eKg").asDouble())).append(" kg");
        if (ctx.has("topContributors") && ctx.path("topContributors").size() > 0) {
            JsonNode top = ctx.path("topContributors").get(0);
            sb.append("\n• Largest contributor: ").append(top.path("category").asText().toLowerCase())
              .append(" (").append(fmt(top.path("pct").asDouble())).append("%)");
        }
        if (ctx.has("openAnomalies") && ctx.path("openAnomalies").asInt() > 0) {
            sb.append("\n• Open anomalies to review: ").append(ctx.path("openAnomalies").asInt());
        }
        if (ctx.path("metrics12mo").size() == 0 && !ctx.has("score")) {
            return "I don't have enough recorded data to summarise your performance yet. Add some "
                    + "environmental records and I'll be able to help.";
        }
        return sb.toString();
    }

    // ---- recommendation rephrasing mode --------------------------------

    private String rephraseRecommendations(JsonNode ctx) {
        // Echo the items with lightly polished wording; numbers are copied verbatim.
        var arr = mapper.createArrayNode();
        for (JsonNode item : ctx.path("items")) {
            var node = mapper.createObjectNode();
            node.put("rank", item.path("rank").asInt());
            node.put("title", item.path("title").asText());
            String rationale = item.path("rationale").asText();
            node.put("rationale", rationale.endsWith(".") ? rationale : rationale + ".");
            arr.add(node);
        }
        try {
            return mapper.writeValueAsString(arr);
        } catch (Exception e) {
            return ctx.path("items").toString();
        }
    }

    // ---- helpers -----------------------------------------------------

    private JsonNode parse(String json) {
        try {
            return json == null || json.isBlank() ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9
                ? String.valueOf((long) Math.rint(v))
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }
}
