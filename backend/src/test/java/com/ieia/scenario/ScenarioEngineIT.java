package com.ieia.scenario;

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
import static org.assertj.core.data.Offset.offset;

/** Hand-checked deterministic what-if maths (docs/04 Stage 7). */
class ScenarioEngineIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "sc-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Sc", "accountType", "INDIVIDUAL", "region", "GLOBAL"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();

        String elec = atId("ELECTRICITY", "electricity_grid");
        String car = atId("TRANSPORT", "car_petrol");
        String mixed = atId("WASTE", "waste_mixed");
        String recy = atId("WASTE", "waste_recycled");
        // two identical months so the average month is unambiguous
        for (String m : List.of("2026-04", "2026-05")) {
            add(elec, m, 500, "kWh");      // 500 * 0.475 = 237.5 kgCO2e ; 500 * 3.6 = 1800 MJ
            add(car, m, 200, "km");        // 200 * 0.17  = 34 kgCO2e
            add(mixed, m, 60, "kg");       // 60 kg landfilled
            add(recy, m, 40, "kg");        // 40 kg diverted -> baseline recycling 40%
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

    private JsonNode simulate(Object body) {
        ResponseEntity<JsonNode> res = rest.exchange("/api/scenarios/simulate", HttpMethod.POST,
                new HttpEntity<>(body, auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().path("data");
    }

    @Test
    void pct_reduction_on_electricity_matches_hand_calculation() {
        JsonNode r = simulate(Map.of(
                "name", "Cut electricity 20%",
                "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of(
                        "target", Map.of("type", "CATEGORY", "value", "ELECTRICITY"),
                        "change", "PCT_REDUCTION", "value", 20))));

        // baseline co2e = 237.5 (elec) + 34 (car) + waste-category co2e; check the electricity delta precisely
        double baseElec = categoryCo2e(r.path("baseline"), "ELECTRICITY");
        double projElec = categoryCo2e(r.path("projected"), "ELECTRICITY");
        assertThat(baseElec).isCloseTo(237.5, offset(0.01));
        assertThat(projElec).isCloseTo(190.0, offset(0.01));                 // 237.5 * 0.8

        assertThat(r.path("deltas").path("energyMj").asDouble()).isCloseTo(-360.0, offset(0.5)); // 1800 * -0.2
        assertThat(r.path("deltas").path("co2eKg").asDouble()).isCloseTo(-47.5, offset(0.1));
        assertThat(r.path("deltas").path("overallScore").asDouble()).isGreaterThan(0); // score improves
        assertThat(r.path("assumptions")).isNotEmpty();
    }

    @Test
    void modal_shift_car_to_metro_moves_half_the_km() {
        JsonNode r = simulate(Map.of(
                "name", "Half my car km to metro",
                "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of(
                        "target", Map.of("type", "ACTIVITY_TYPE", "value", "car_petrol"),
                        "change", "MODAL_SHIFT", "value", 50,
                        "params", Map.of("toActivityCode", "metro_rail", "sharePct", 50)))));

        // car 34 kgCO2e/mo; shift 50% -> 17 shifts; 17/0.17 = 100 km; 100 * 0.035 = 3.5 added; net -13.5
        double baseTransport = categoryCo2e(r.path("baseline"), "TRANSPORT");
        double projTransport = categoryCo2e(r.path("projected"), "TRANSPORT");
        assertThat(baseTransport).isCloseTo(34.0, offset(0.01));
        assertThat(projTransport).isCloseTo(20.5, offset(0.05));
        assertThat(r.path("deltas").path("co2eKg").asDouble()).isCloseTo(-13.5, offset(0.05));
    }

    @Test
    void recycling_rate_increase_reduces_landfilled_mass_and_waste_co2e() {
        JsonNode r = simulate(Map.of(
                "name", "Recycle 80%",
                "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of(
                        "target", Map.of("type", "CATEGORY", "value", "WASTE"),
                        "change", "RECYCLING_RATE", "value", 80))));

        // generated 100 kg/mo, baseline 40% diverted. New: 80% -> 20 kg landfilled (was 60).
        assertThat(r.path("baseline").path("wasteKg").asDouble()).isCloseTo(60.0, offset(0.5));
        assertThat(r.path("projected").path("wasteKg").asDouble()).isCloseTo(20.0, offset(0.5));
        assertThat(r.path("projected").path("recycledFraction").asDouble()).isCloseTo(0.8, offset(0.001));
        assertThat(r.path("deltas").path("wasteKg").asDouble()).isCloseTo(-40.0, offset(0.5));
    }

    @Test
    void simulation_is_deterministic() {
        Object body = Map.of("name", "x", "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of(
                        "target", Map.of("type", "CATEGORY", "value", "ELECTRICITY"),
                        "change", "PCT_REDUCTION", "value", 15)));
        JsonNode a = simulate(body);
        JsonNode b = simulate(body);
        assertThat(a.path("projected").toString()).isEqualTo(b.path("projected").toString());
        assertThat(a.path("deltas").toString()).isEqualTo(b.path("deltas").toString());
    }

    @Test
    void saved_scenario_round_trips_and_compare_returns_aligned_bundles() {
        Map<String, Object> body1 = Map.of("name", "20% elec cut",
                "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of("target", Map.of("type", "CATEGORY", "value", "ELECTRICITY"),
                        "change", "PCT_REDUCTION", "value", 20)));
        Map<String, Object> body2 = Map.of("name", "40% elec cut",
                "basePeriodFrom", "2026-04", "basePeriodTo", "2026-05",
                "adjustments", List.of(Map.of("target", Map.of("type", "CATEGORY", "value", "ELECTRICITY"),
                        "change", "PCT_REDUCTION", "value", 40)));

        String id1 = rest.exchange("/api/scenarios", HttpMethod.POST, new HttpEntity<>(body1, auth()), JsonNode.class)
                .getBody().path("data").path("id").asText();
        String id2 = rest.exchange("/api/scenarios", HttpMethod.POST, new HttpEntity<>(body2, auth()), JsonNode.class)
                .getBody().path("data").path("id").asText();

        ResponseEntity<JsonNode> list = rest.exchange("/api/scenarios", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(list.getBody().path("data").path("totalElements").asInt()).isEqualTo(2);

        ResponseEntity<JsonNode> cmp = rest.exchange("/api/scenarios/compare", HttpMethod.POST,
                new HttpEntity<>(Map.of("scenarioIds", List.of(id1, id2)), auth()), JsonNode.class);
        JsonNode d = cmp.getBody().path("data");
        assertThat(d.path("scenarios")).hasSize(2);
        double delta1 = d.path("scenarios").get(0).path("deltas").path("co2eKg").asDouble();
        double delta2 = d.path("scenarios").get(1).path("deltas").path("co2eKg").asDouble();
        assertThat(Math.abs(delta2)).isGreaterThan(Math.abs(delta1)); // 40% cut saves more than 20%
    }

    private static double categoryCo2e(JsonNode bundle, String category) {
        return bundle.path("co2eByCategory").path(category).asDouble();
    }
}
