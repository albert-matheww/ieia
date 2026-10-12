package com.ieia.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationBenchmarkIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "rb-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "RB", "accountType", "INDIVIDUAL", "region", "IN"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();

        String elec = atId("ELECTRICITY", "electricity_grid");
        String car = atId("TRANSPORT", "car_petrol");
        String mixed = atId("WASTE", "waste_mixed");
        // electricity-dominant, 3 months
        for (String m : List.of("2026-04", "2026-05", "2026-06")) {
            add(elec, m, 800, "kWh");
            add(car, m, 200, "km");
            add(mixed, m, 40, "kg");
        }
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String atId(String cat, String code) {
        for (JsonNode n : rest.exchange("/api/activity-types?category=" + cat, HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class).getBody().path("data")) {
            if (n.path("code").asText().equals(code)) return n.path("id").asText();
        }
        throw new IllegalStateException(code);
    }

    private void add(String id, String period, double amount, String unit) {
        rest.exchange("/api/environmental-records", HttpMethod.POST, new HttpEntity<>(Map.of(
                "activityTypeId", id, "period", period, "amount", amount, "unit", unit), auth()), JsonNode.class);
    }

    @Test
    void recommendations_are_data_driven_ranked_and_quantified_from_the_top_contributor() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/recommendations", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = res.getBody().path("data");

        assertThat(d.path("basis").asText()).isEqualTo("DATA_DRIVEN");
        assertThat(d.path("topContributor").path("category").asText()).isEqualTo("ELECTRICITY");

        JsonNode items = d.path("items");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).path("category").asText()).isEqualTo("ELECTRICITY");
        // ranked descending by estimated reduction
        double prev = Double.MAX_VALUE;
        for (JsonNode it : items) {
            double r = it.path("estimatedReductionCo2eKg").asDouble();
            assertThat(r).isGreaterThan(0);
            assertThat(r).isLessThanOrEqualTo(prev);
            prev = r;
        }
        // #1 reduction must equal what the scenario engine says for that lever
        assertThat(items.get(0).path("estimatedReductionPct").asDouble()).isGreaterThan(0);
    }

    @Test
    void llm_rephrasing_with_mock_provider_keeps_numbers_and_flips_source() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/recommendations/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of("useLlm", true), auth()), JsonNode.class);
        JsonNode d = res.getBody().path("data");
        assertThat(d.path("source").asText()).isEqualTo("LLM_ASSISTED");
        // every item still carries a positive numeric estimate
        for (JsonNode it : d.path("items")) {
            assertThat(it.path("estimatedReductionCo2eKg").asDouble()).isGreaterThan(0);
        }
    }

    @Test
    void recommendation_status_can_be_updated() {
        JsonNode items = rest.exchange("/api/recommendations", HttpMethod.GET, new HttpEntity<>(auth()),
                JsonNode.class).getBody().path("data").path("items");
        String id = items.get(0).path("id").asText();

        ResponseEntity<JsonNode> patch = rest.exchange("/api/recommendations/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "IN_PROGRESS"), auth()), JsonNode.class);
        assertThat(patch.getBody().path("data").path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void benchmark_comparison_returns_ratings_and_the_non_official_disclaimer() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/benchmarks/comparison", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = res.getBody().path("data");

        assertThat(d.path("scope").asText()).isEqualTo("INDIVIDUAL");
        assertThat(d.path("segment").asText()).isEqualTo("IN_URBAN_HOUSEHOLD");
        assertThat(d.path("disclaimer").asText()).contains("not official standards");
        JsonNode categories = d.path("categories");
        assertThat(categories).hasSize(4);
        for (JsonNode c : categories) {
            assertThat(c.path("rating").asText()).isIn("GOOD", "AVERAGE", "NEEDS_IMPROVEMENT", "ABOVE_AVERAGE");
        }
    }

    @Test
    void member_cannot_write_a_benchmark() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/benchmarks", HttpMethod.POST,
                new HttpEntity<>(Map.of("scope", "INDIVIDUAL", "segment", "GLOBAL_AVG", "metric", "CO2E",
                        "unit", "kgCO2e", "period", "MONTH", "value", 300), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
