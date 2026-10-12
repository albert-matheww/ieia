package com.ieia.integration.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.common.exception.ApiException;
import com.ieia.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions provider. Active when {@code ieia.llm.provider=OPENAI}. */
@Component
@ConditionalOnProperty(name = "ieia.llm.provider", havingValue = "OPENAI")
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final RestClient http;
    private final String model;
    private final boolean configured;

    public OpenAiLlmClient(AppProperties props) {
        var p = props.getLlm().getOpenai();
        this.configured = p.getApiKey() != null && !p.getApiKey().isBlank();
        this.model = props.getLlm().getModel();
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(4000);
        rf.setReadTimeout((int) Duration.ofMillis(props.getLlm().getTimeoutMs()).toMillis());
        this.http = RestClient.builder()
                .baseUrl(p.getBaseUrl())
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (configured ? p.getApiKey() : "unset"))
                .build();
        log.info("LLM provider: OPENAI (model {}, key {})", model, configured ? "present" : "MISSING");
    }

    @Override public boolean isAvailable() { return configured; }
    @Override public String providerName() { return "OPENAI"; }

    @Override
    public Response complete(Request request) {
        if (!configured) throw ApiException.llmUnavailable("OPENAI_API_KEY is not set");
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemWithContext(request)));
        if (request.history() != null) {
            for (Turn t : request.history()) messages.add(Map.of("role", t.role(), "content", t.content()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        try {
            JsonNode res = http.post().uri("/chat/completions")
                    .body(Map.of("model", model, "messages", messages,
                            "max_tokens", request.maxTokens(), "temperature", 0.2))
                    .retrieve().body(JsonNode.class);
            String text = res.path("choices").path(0).path("message").path("content").asText();
            return new Response(text, res.path("model").asText(model),
                    res.path("usage").path("prompt_tokens").asInt(),
                    res.path("usage").path("completion_tokens").asInt(),
                    Map.of("provider", "OPENAI"));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.llmUnavailable("OpenAI request failed: " + e.getMessage());
        }
    }

    static String systemWithContext(Request r) {
        return r.systemPrompt() + "\n\nCONTEXT (the only figures you may use):\n" + r.contextJson();
    }
}
