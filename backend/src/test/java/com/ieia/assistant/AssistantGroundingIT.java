package com.ieia.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.integration.llm.LlmClient;
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

/** The assistant must never surface an invented number (docs/05 §5.2). */
class AssistantGroundingIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @MockBean
    LlmClient llm;

    String token;

    @BeforeEach
    void setUp() {
        Mockito.when(llm.isAvailable()).thenReturn(true);
        Mockito.when(llm.providerName()).thenReturn("MOCK");

        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", "ai-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!",
                "fullName", "AI", "accountType", "INDIVIDUAL", "region", "GLOBAL"), JsonNode.class);
        token = reg.getBody().path("data").path("accessToken").asText();

        String elec = atId("ELECTRICITY", "electricity_grid");
        add(elec, "2026-05", 500, "kWh");
        add(elec, "2026-06", 800, "kWh");   // 800*0.475 = 380 kgCO2e
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

    private LlmClient.Response reply(String text) {
        return new LlmClient.Response(text, "mock", 0, 10, Map.of());
    }

    @Test
    void a_reply_that_only_uses_context_numbers_passes_grounding() {
        Mockito.when(llm.complete(any()))
                .thenReturn(reply("Your CO₂e for 2026-06 is 380 kg, up from 237.5 kg."));

        ResponseEntity<JsonNode> res = rest.exchange("/api/ai/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "How much CO2e this month?"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = res.getBody().path("data");
        assertThat(d.path("groundingCheckPassed").asBoolean()).isTrue();
        assertThat(d.path("reply").asText()).contains("380");
        assertThat(d.path("disclaimers")).isNotEmpty();
    }

    @Test
    void an_invented_number_is_caught_retried_then_replaced_by_a_safe_fallback() {
        // both attempts fabricate "999999"
        Mockito.when(llm.complete(any()))
                .thenReturn(reply("Your footprint is 999999 kg CO₂e, mostly from air travel worth $4200."));

        ResponseEntity<JsonNode> res = rest.exchange("/api/ai/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "Summarise my footprint"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = res.getBody().path("data");

        assertThat(d.path("groundingCheckPassed").asBoolean()).isFalse();
        assertThat(d.path("reply").asText()).doesNotContain("999999").doesNotContain("4200");
        assertThat(d.path("reply").asText()).contains("verify against your data");
        Mockito.verify(llm, Mockito.times(2)).complete(any());   // original + strict retry
    }

    @Test
    void llm_unavailable_returns_503_not_a_fabricated_answer() {
        Mockito.when(llm.isAvailable()).thenReturn(false);

        ResponseEntity<JsonNode> res = rest.exchange("/api/ai/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "hello"), auth()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().path("error").path("code").asText()).isEqualTo("LLM_UNAVAILABLE");
    }

    @Test
    void conversation_history_is_persisted_and_retrievable() {
        Mockito.when(llm.complete(any())).thenReturn(reply("Your score is fine."));
        JsonNode first = rest.exchange("/api/ai/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "How am I doing?"), auth()), JsonNode.class)
                .getBody().path("data");
        String convId = first.path("conversationId").asText();

        rest.exchange("/api/ai/chat", HttpMethod.POST,
                new HttpEntity<>(Map.of("conversationId", convId, "message", "And last month?"), auth()),
                JsonNode.class);

        ResponseEntity<JsonNode> detail = rest.exchange("/api/ai/conversations/" + convId, HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class);
        assertThat(detail.getBody().path("data").path("messages")).hasSize(4); // 2 user + 2 assistant
    }
}
