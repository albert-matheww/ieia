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
import java.util.Map;

/** Local Ollama provider (no API key). Active when {@code ieia.llm.provider=OLLAMA}. */
@Component
@ConditionalOnProperty(name = "ieia.llm.provider", havingValue = "OLLAMA")
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    private final RestClient http;
    private final String model;

    public OllamaLlmClient(AppProperties props) {
        var p = props.getLlm().getOllama();
        this.model = props.getLlm().getModel();
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout((int) Duration.ofMillis(props.getLlm().getTimeoutMs()).toMillis());
        this.http = RestClient.builder().baseUrl(p.getBaseUrl()).requestFactory(rf).build();
        log.info("LLM provider: OLLAMA (model {}, base {})", model, p.getBaseUrl());
    }

    @Override
    public boolean isAvailable() {
        try {
            http.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public String providerName() { return "OLLAMA"; }

    @Override
    public Response complete(Request request) {
        String prompt = OpenAiLlmClient.systemWithContext(request) + "\n\nUser: " + request.userPrompt()
                + "\nAssistant:";
        try {
            JsonNode res = http.post().uri("/api/generate")
                    .body(Map.of("model", model, "prompt", prompt, "stream", false,
                            "options", Map.of("temperature", 0.2, "num_predict", request.maxTokens())))
                    .retrieve().body(JsonNode.class);
            return new Response(res.path("response").asText(), model,
                    res.path("prompt_eval_count").asInt(), res.path("eval_count").asInt(),
                    Map.of("provider", "OLLAMA"));
        } catch (RuntimeException e) {
            throw ApiException.llmUnavailable("Ollama request failed: " + e.getMessage());
        }
    }
}
