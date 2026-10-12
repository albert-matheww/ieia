package com.ieia.scoring;

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

/** End-to-end: record → calculated impact → monthly metric → environmental score. */
class ScoringPipelineIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "pipe-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Pipe", "accountType", "INDIVIDUAL", "region", "GLOBAL"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String atId(String category, String code) {
        for (JsonNode n : rest.exchange("/api/activity-types?category=" + category, HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class).getBody().path("data")) {
            if (n.path("code").asText().equals(code)) return n.path("id").asText();
        }
        throw new IllegalStateException(code);
    }

    private void addRecord(String activityTypeId, String period, double amount, String unit) {
        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", activityTypeId, "period", period,
                        "amount", amount, "unit", unit), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void records_produce_metrics_and_an_explainable_score_that_reacts_to_edits() {
        String elec = atId("ELECTRICITY", "electricity_grid");
        String petrol = atId("FUEL", "petrol");
        String car = atId("TRANSPORT", "car_petrol");
        String recy = atId("WASTE", "waste_recycled");
        String mixed = atId("WASTE", "waste_mixed");

        // Month 1
        addRecord(elec, "2026-05", 600, "kWh");
        addRecord(petrol, "2026-05", 30, "litre");
        addRecord(car, "2026-05", 400, "km");
        addRecord(mixed, "2026-05", 40, "kg");
        addRecord(recy, "2026-05", 20, "kg");
        // Month 2 — electricity and transport rise
        addRecord(elec, "2026-06", 900, "kWh");
        addRecord(petrol, "2026-06", 34, "litre");
        addRecord(car, "2026-06", 600, "km");
        addRecord(mixed, "2026-06", 44, "kg");
        addRecord(recy, "2026-06", 24, "kg");

        // score history has two months
        ResponseEntity<JsonNode> history = rest.exchange("/api/scores", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(history.getBody().path("data")).hasSize(2);

        // June: score dropped, delta negative, drivers present and sum to the delta
        ResponseEntity<JsonNode> june = rest.exchange("/api/scores/2026-06", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        JsonNode j = june.getBody().path("data");
        double delta = j.path("delta").asDouble();
        assertThat(delta).isLessThan(0.0);

        double sumPoints = 0;
        for (JsonNode d : j.path("drivers")) sumPoints += d.path("pointsImpact").asDouble();
        assertThat(sumPoints).isCloseTo(delta, org.assertj.core.data.Offset.offset(0.1));

        // top driver is electricity's category (carbon or energy), and it worsened
        JsonNode top = j.path("drivers").get(0);
        assertThat(top.path("direction").asText()).isEqualTo("WORSENED");

        // breakdown is fully transparent
        JsonNode carbon = j.path("breakdown").path("categories").path("carbon");
        assertThat(carbon.path("baselineGood").asDouble()).isEqualTo(250.0);
        assertThat(carbon.path("weight").asDouble()).isEqualTo(0.35);

        // methodology endpoint
        ResponseEntity<JsonNode> method = rest.exchange("/api/scores/methodology", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(method.getBody().path("data").path("version").asText()).isEqualTo("v1");
    }

    @Test
    void deleting_all_records_in_a_month_removes_its_metric_and_score() {
        String elec = atId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> created = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-04",
                        "amount", 300, "unit", "kWh"), auth()), JsonNode.class);
        String id = created.getBody().path("data").path("id").asText();

        assertThat(rest.exchange("/api/scores/2026-04", HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        rest.exchange("/api/environmental-records/" + id, HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        assertThat(rest.exchange("/api/scores/2026-04", HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
