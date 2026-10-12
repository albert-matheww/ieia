package com.ieia.report;

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

/** Reports assemble from stored data; PDF path yields a real downloadable file. */
class ReportControllerIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;
    String token;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "rep-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Rep", "accountType", "INDIVIDUAL", "region", "IN"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();

        String elec = atId("ELECTRICITY", "electricity_grid");
        String car = atId("TRANSPORT", "car_petrol");
        for (int m = 4; m <= 7; m++) {
            String p = String.format("2026-%02d", m);
            add(elec, p, 600 + m * 10, "kWh");
            add(car, p, 300, "km");
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
    void json_report_contains_all_sections_built_from_stored_data() {
        ResponseEntity<JsonNode> create = rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(Map.of(
                "title", "Test report", "from", "2026-04", "to", "2026-07", "format", "JSON"), auth()), JsonNode.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = create.getBody().path("data").path("id").asText();
        assertThat(create.getBody().path("data").path("status").asText()).isEqualTo("READY");

        ResponseEntity<JsonNode> detail = rest.exchange("/api/reports/" + id, HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        JsonNode p = detail.getBody().path("data").path("payload");
        assertThat(p.has("summary")).isTrue();
        assertThat(p.has("scores")).isTrue();
        assertThat(p.has("trends")).isTrue();
        assertThat(p.has("contributors")).isTrue();
        assertThat(p.has("recommendations")).isTrue();
        assertThat(p.has("benchmarks")).isTrue();
        assertThat(p.has("methodology")).isTrue();
        assertThat(p.path("summary").path("totals").path("co2eKg").asDouble()).isGreaterThan(0);
        assertThat(p.path("scores").size()).isEqualTo(4);
        assertThat(p.path("methodology").path("scoringVersion").asText()).isEqualTo("v1");
    }

    @Test
    void selective_include_only_emits_requested_sections() {
        ResponseEntity<JsonNode> create = rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(Map.of(
                "from", "2026-04", "to", "2026-07", "format", "JSON",
                "include", List.of("summary", "methodology")), auth()), JsonNode.class);
        String id = create.getBody().path("data").path("id").asText();
        JsonNode p = rest.exchange("/api/reports/" + id, HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class)
                .getBody().path("data").path("payload");
        assertThat(p.has("summary")).isTrue();
        assertThat(p.has("methodology")).isTrue();
        assertThat(p.has("scores")).isFalse();
        assertThat(p.has("trends")).isFalse();
    }

    @Test
    void pdf_report_generates_and_downloads_as_a_real_pdf() {
        // async=false in the test profile -> status is READY immediately
        ResponseEntity<JsonNode> create = rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(Map.of(
                "from", "2026-04", "to", "2026-07", "format", "PDF"), auth()), JsonNode.class);
        String id = create.getBody().path("data").path("id").asText();
        assertThat(create.getBody().path("data").path("status").asText()).isEqualTo("READY");
        assertThat(create.getBody().path("data").path("fileSizeBytes").asLong()).isGreaterThan(1000);

        ResponseEntity<byte[]> dl = rest.exchange("/api/reports/" + id + "/download", HttpMethod.GET,
                new HttpEntity<>(auth()), byte[].class);
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        byte[] body = dl.getBody();
        assertThat(body).isNotNull();
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void one_user_cannot_download_another_users_report() {
        String id = rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(Map.of(
                "from", "2026-04", "to", "2026-07", "format", "PDF"), auth()), JsonNode.class)
                .getBody().path("data").path("id").asText();

        ResponseEntity<JsonNode> reg2 = rest.postForEntity("/api/auth/register", Map.of(
                "email", "other-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Other", "accountType", "INDIVIDUAL"), JsonNode.class);
        HttpHeaders h2 = new HttpHeaders();
        h2.setBearerAuth(reg2.getBody().path("data").path("accessToken").asText());

        ResponseEntity<JsonNode> res = rest.exchange("/api/reports/" + id, HttpMethod.GET,
                new HttpEntity<>(h2), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
