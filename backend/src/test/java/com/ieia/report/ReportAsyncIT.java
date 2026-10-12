package com.ieia.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production profile generates PDF reports on an {@code @Async} worker. That worker
 * reads the report row back by id, so the row must be committed before the job is
 * dispatched — otherwise it never leaves PENDING. This exercises that path with
 * {@code ieia.reports.async=true} (the other report ITs run it synchronously).
 */
@TestPropertySource(properties = "ieia.reports.async=true")
class ReportAsyncIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void async_pdf_report_reaches_ready_and_downloads() throws InterruptedException {
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "async-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "Async", "accountType", "INDIVIDUAL", "region", "IN"), JsonNode.class);
        String token = reg.getBody().path("data").path("accessToken").asText();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        String elecId = null;
        for (JsonNode n : rest.exchange("/api/activity-types?category=ELECTRICITY", HttpMethod.GET,
                new HttpEntity<>(h), JsonNode.class).getBody().path("data")) {
            if (n.path("code").asText().equals("electricity_grid")) elecId = n.path("id").asText();
        }
        rest.exchange("/api/environmental-records", HttpMethod.POST, new HttpEntity<>(Map.of(
                "activityTypeId", elecId, "period", "2026-05", "amount", 640, "unit", "kWh"), h), JsonNode.class);

        ResponseEntity<JsonNode> create = rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(Map.of(
                "from", "2026-04", "to", "2026-07", "format", "PDF"), h), JsonNode.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = create.getBody().path("data").path("id").asText();
        assertThat(create.getBody().path("data").path("status").asText()).isIn("PENDING", "GENERATING", "READY");

        String status = "PENDING";
        long fileSize = 0;
        for (int i = 0; i < 80 && !"READY".equals(status); i++) {
            Thread.sleep(250);
            JsonNode d = rest.exchange("/api/reports/" + id, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class)
                    .getBody().path("data");
            status = d.path("status").asText();
            fileSize = d.path("fileSizeBytes").asLong();
            assertThat(status).isNotEqualTo("FAILED");
        }
        assertThat(status).isEqualTo("READY");
        assertThat(fileSize).isGreaterThan(1000);

        ResponseEntity<byte[]> dl = rest.exchange("/api/reports/" + id + "/download", HttpMethod.GET,
                new HttpEntity<>(h), byte[].class);
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(dl.getBody(), 0, 5)).isEqualTo("%PDF-");
    }
}
