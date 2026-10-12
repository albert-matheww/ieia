package com.ieia.integration.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.common.exception.ApiException;
import com.ieia.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API provider. Active when {@code ieia.llm.provider=ANTHROPIC}. */
@Component
@ConditionalOnProperty(name = "ieia.llm.provider", havingValue = "ANTHROPIC")
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final RestClient http;
    private final String model;
    private final boolean configured;

    public AnthropicLlmClient(AppProperties props) {
        var p = props.getLlm().getAnthropic();
        this.configured = p.getApiKey() != null && !p.getApiKey().isBlank();
        this.model = props.getLlm().getModel();
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(4000);
        rf.setReadTimeout((int) Duration.ofMillis(props.getLlm().getTimeoutMs()).toMillis());
        this.http = RestClient.builder()
                .baseUrl(p.getBaseUrl())
                .requestFactory(rf)
                .defaultHeader("x-api-key", configured ? p.getApiKey() : "unset")
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        log.info("LLM provider: ANTHROPIC (model {}, key {})", model, configured ? "present" : "MISSING");
    }

    @Override public boolean isAvailable() { return configured; }
    @Override public String providerName() { return "ANTHROPIC"; }

    @Override
    public Response complete(Request request) {
        if (!configured) throw ApiException.llmUnavailable("ANTHROPIC_API_KEY is not set");
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.history() != null) {
            for (Turn t : request.history()) messages.add(Map.of("role", t.role(), "content", t.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        try {
            JsonNode res = http.post().uri("/v1/messages")
                    .body(Map.of("model", model, "max_tokens", request.maxTokens(), "temperature", 0.2,
                            "system", OpenAiLlmClient.systemWithContext(request), "messages", messages))
                    .retrieve().body(JsonNode.class);
            String text = res.path("content").path(0).path("text").asText();
            return new Response(text, res.path("model").asText(model),
                    res.path("usage").path("input_tokens").asInt(),
                    res.path("usage").path("output_tokens").asInt(),
                    Map.of("provider", "ANTHROPIC"));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.llmUnavailable("Anthropic request failed: " + e.getMessage());
        }
    }
}
