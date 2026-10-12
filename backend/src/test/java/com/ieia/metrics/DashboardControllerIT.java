package com.ieia.metrics;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "dash-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Dash", "accountType", "INDIVIDUAL", "region", "GLOBAL"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();
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
    void dashboard_is_built_entirely_from_stored_data_with_period_deltas() {
        String elec = atId("ELECTRICITY", "electricity_grid");
        String car = atId("TRANSPORT", "car_petrol");
        add(elec, "2026-05", 500, "kWh");
        add(car, "2026-05", 300, "km");
        add(elec, "2026-06", 750, "kWh");   // +50%
        add(car, "2026-06", 300, "km");

        ResponseEntity<JsonNode> res = rest.exchange("/api/dashboard", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = res.getBody().path("data");

        assertThat(d.path("period").asText()).isEqualTo("2026-06");
        JsonNode co2e = d.path("summary").path("co2eKg");
        // 750 * 0.475 (GLOBAL) = 356.25 ; prev 500 * 0.475 = 237.5 ; +50%
        assertThat(co2e.path("value").asDouble()).isEqualTo(356.25);
        assertThat(co2e.path("previous").asDouble()).isEqualTo(237.5);
        assertThat(co2e.path("deltaPct").asDouble()).isEqualTo(50.0);

        assertThat(d.path("charts").path("co2eTrend")).hasSize(2);
        assertThat(d.path("charts").path("scoreTrend")).hasSize(2);
        assertThat(d.path("topContributors").get(0).path("activityCode").asText()).isEqualTo("electricity_grid");
        assertThat(d.path("dataQuality").path("monthsOfHistory").asInt()).isEqualTo(2);
    }

    @Test
    void trends_endpoint_returns_requested_series_only() {
        String elec = atId("ELECTRICITY", "electricity_grid");
        add(elec, "2026-05", 400, "kWh");
        add(elec, "2026-06", 500, "kWh");

        ResponseEntity<JsonNode> res = rest.exchange("/api/impact/trends?metrics=CO2E,SCORE",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        JsonNode series = res.getBody().path("data").path("series");
        assertThat(series.has("CO2E")).isTrue();
        assertThat(series.has("SCORE")).isTrue();
        assertThat(series.has("WATER")).isFalse();
        assertThat(series.path("CO2E")).hasSize(2);
    }

    @Test
    void explain_endpoint_narrates_the_score_change() {
        String elec = atId("ELECTRICITY", "electricity_grid");
        add(elec, "2026-05", 400, "kWh");
        add(elec, "2026-06", 900, "kWh");

        ResponseEntity<JsonNode> res = rest.exchange("/api/impact/explain", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        JsonNode d = res.getBody().path("data");
        assertThat(d.path("scoreDelta").asDouble()).isLessThan(0);
        assertThat(d.path("narrative").asText()).contains("decreased");
        assertThat(d.path("drivers")).isNotEmpty();
    }

    @Test
    void dashboard_on_a_user_with_no_data_is_a_clean_404() {
        ResponseEntity<JsonNode> res = rest.exchange("/api/dashboard", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().path("error").path("code").asText()).isEqualTo("NOT_FOUND");
    }
}
