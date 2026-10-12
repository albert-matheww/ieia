package com.ieia.record;

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

class RecordControllerIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private String token;

    @BeforeEach
    void registerUser() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "rec-" + UUID.randomUUID() + "@example.com",
                "password", "Sup3rSecret!", "fullName", "Rec User",
                "accountType", "INDIVIDUAL", "region", "IN"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String activityTypeId(String category, String code) {
        ResponseEntity<JsonNode> res = rest.exchange("/api/activity-types?category=" + category,
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        for (JsonNode n : res.getBody().path("data")) {
            if (n.path("code").asText().equals(code)) return n.path("id").asText();
        }
        throw new IllegalStateException("activity type not found: " + code);
    }

    @Test
    void create_electricity_record_computes_regional_co2e_and_energy() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");

        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 250, "unit", "kWh"), auth()), JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode impacts = res.getBody().path("data").path("impacts");
        assertThat(impacts).hasSize(2);
        double co2e = impactValue(impacts, "CO2E");
        double energy = impactValue(impacts, "ENERGY");
        assertThat(co2e).isEqualTo(179.0);     // 250 * 0.716 (IN factor)
        assertThat(energy).isEqualTo(900.0);   // 250 * 3.6
    }

    @Test
    void mwh_amount_is_converted_before_the_factor_is_applied() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 2, "unit", "MWh"), auth()), JsonNode.class);
        assertThat(impactValue(res.getBody().path("data").path("impacts"), "CO2E")).isEqualTo(1432.0);
    }

    @Test
    void update_recomputes_impacts_and_delete_removes_them() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> created = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 100, "unit", "kWh"), auth()), JsonNode.class);
        String id = created.getBody().path("data").path("id").asText();

        ResponseEntity<JsonNode> updated = rest.exchange("/api/environmental-records/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 400, "unit", "kWh"), auth()), JsonNode.class);
        assertThat(impactValue(updated.getBody().path("data").path("impacts"), "CO2E"))
                .isEqualTo(286.4); // 400 * 0.716

        ResponseEntity<Void> del = rest.exchange("/api/environmental-records/" + id, HttpMethod.DELETE,
                new HttpEntity<>(auth()), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> get = rest.exchange("/api/environmental-records/" + id, HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void future_period_is_rejected_422() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2099-01",
                        "amount", 10, "unit", "kWh"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().path("error").path("code").asText()).isEqualTo("UNPROCESSABLE");
    }

    @Test
    void unit_not_in_allowed_list_is_rejected_422() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 10, "unit", "litre"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void negative_amount_is_rejected_400() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", -3, "unit", "kWh"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void a_user_cannot_read_another_users_record_404() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        String id = rest.exchange("/api/environmental-records", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "period", "2026-06",
                        "amount", 50, "unit", "kWh"), auth()), JsonNode.class)
                .getBody().path("data").path("id").asText();

        // second user
        ResponseEntity<JsonNode> reg2 = rest.postForEntity("/api/auth/register", Map.of(
                "email", "other-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Other", "accountType", "INDIVIDUAL"), JsonNode.class);
        HttpHeaders h2 = new HttpHeaders();
        h2.setBearerAuth(reg2.getBody().path("data").path("accessToken").asText());

        ResponseEntity<JsonNode> res = rest.exchange("/api/environmental-records/" + id, HttpMethod.GET,
                new HttpEntity<>(h2), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void admin_only_guard_blocks_member_from_creating_emission_factor_403() {
        String elec = activityTypeId("ELECTRICITY", "electricity_grid");
        ResponseEntity<JsonNode> res = rest.exchange("/api/emission-factors", HttpMethod.POST,
                new HttpEntity<>(Map.of("activityTypeId", elec, "impactType", "CO2E", "unitFrom", "kWh",
                        "unitTo", "kgCO2e", "value", 0.5, "source", "x", "effectiveFrom", "2026-01-01"), auth()),
                JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static double impactValue(JsonNode impacts, String type) {
        for (JsonNode i : impacts) {
            if (i.path("impactType").asText().equals(type)) return i.path("value").asDouble();
        }
        throw new AssertionError("no impact of type " + type);
    }
}
