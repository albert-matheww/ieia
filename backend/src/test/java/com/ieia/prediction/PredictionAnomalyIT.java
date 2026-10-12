package com.ieia.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.integration.ml.MlServiceClient;
import com.ieia.integration.ml.dto.MlAnomalyResponse;
import com.ieia.integration.ml.dto.MlPredictResponse;
import com.ieia.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.ArgumentMatchers.any;

/** Backend <-> ML integration with the ML tier stubbed (docs/04 Stage 6). */
class PredictionAnomalyIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @MockBean
    MlServiceClient ml;

    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "ml-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "ML", "accountType", "INDIVIDUAL", "region", "IN"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();

        String elec = atId("ELECTRICITY", "electricity_grid");
        String[] mths = {"2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-06"};
        int[] kwh = {500, 520, 510, 540, 560, 900};
        for (int i = 0; i < mths.length; i++) {
            add(elec, mths[i], kwh[i], "kWh");
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
    void prediction_calls_ml_persists_and_then_serves_from_cache() {
        Mockito.when(ml.predict(any())).thenReturn(new MlPredictResponse(
                "CO2E", "GradientBoostingRegressor",
                List.of(new MlPredictResponse.ForecastPoint("2025-07", 640.0, 560.0, 720.0)),
                new MlPredictResponse.Evaluation(20.0, 25.0, 0.7, 3, 4),
                List.of(new MlPredictResponse.FeatureImportance("roll_mean_3", 0.5)),
                6, "based on 6 months", List.of("factors assumed constant"), 42));

        ResponseEntity<JsonNode> first = rest.exchange("/api/predictions?metric=CO2E&horizon=1",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = first.getBody().path("data");
        assertThat(d.path("predictedValue").asDouble()).isEqualTo(640.0);
        assertThat(d.path("model").path("name").asText()).isEqualTo("GradientBoostingRegressor");
        assertThat(d.path("cached").asBoolean()).isFalse();

        ResponseEntity<JsonNode> second = rest.exchange("/api/predictions?metric=CO2E&horizon=1",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(second.getBody().path("data").path("cached").asBoolean()).isTrue();

        Mockito.verify(ml, Mockito.times(1)).predict(any());  // second call served from DB
    }

    @Test
    void prediction_maps_ml_outage_to_503_without_fabricating() {
        Mockito.when(ml.predict(any()))
                .thenThrow(com.ieia.common.exception.ApiException.mlUnavailable("down", new RuntimeException()));

        ResponseEntity<JsonNode> res = rest.exchange("/api/predictions?metric=CO2E&horizon=1",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().path("error").path("code").asText()).isEqualTo("ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void anomaly_scan_persists_flagged_months_with_a_co2e_estimate_and_respects_review_state() {
        Mockito.when(ml.detectAnomalies(any())).thenReturn(new MlAnomalyResponse(
                List.of(new MlAnomalyResponse.Item("2025-06", "ELECTRICITY", "CONSENSUS", "SPIKE", "HIGH",
                        900.0, 545.0, 355.0, 65.14, 3.9, 3)),
                Map.of("flagged", 1), 42));

        ResponseEntity<JsonNode> scan = rest.exchange("/api/anomalies/scan", HttpMethod.POST,
                new HttpEntity<>(Map.of("sensitivity", "MEDIUM"), auth()), JsonNode.class);
        assertThat(scan.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode d = scan.getBody().path("data");
        assertThat(d.path("newlyOpened").asInt()).isEqualTo(1);
        JsonNode a = d.path("anomalies").get(0);
        assertThat(a.path("metric").asText()).isEqualTo("ELECTRICITY");
        // 355 kWh deviation * 0.716 (IN grid CO2E factor) ≈ 254.18
        assertThat(a.path("estimatedCo2eImpactKg").asDouble()).isCloseTo(254.18,
                org.assertj.core.data.Offset.offset(0.1));

        String id = a.path("id").asText();
        rest.exchange("/api/anomalies/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DISMISSED"), auth()), JsonNode.class);

        // re-scan: the dismissed anomaly is not reopened
        ResponseEntity<JsonNode> rescan = rest.exchange("/api/anomalies/scan", HttpMethod.POST,
                new HttpEntity<>(Map.of("sensitivity", "MEDIUM"), auth()), JsonNode.class);
        assertThat(rescan.getBody().path("data").path("newlyOpened").asInt()).isZero();

        ResponseEntity<JsonNode> open = rest.exchange("/api/anomalies?status=OPEN", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(open.getBody().path("data").path("totalElements").asInt()).isZero();
    }
}
