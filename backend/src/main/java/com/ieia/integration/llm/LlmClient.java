package com.ieia.integration.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic LLM abstraction (docs/01-architecture.md §3.2, brief §14/§10).
 * The concrete provider is chosen by {@code ieia.llm.provider}; {@code MOCK} is the
 * default so the whole platform runs with no API key.
 *
 * <p>Contract: the model may only <b>phrase</b> answers over the numbers supplied
 * in {@link Request#contextJson()}. Callers are responsible for numeric
 * post-checks (see {@code assistant/AssistantService}).
 */
public interface LlmClient {

    Response complete(Request request);

    /** Whether the configured provider is usable right now (key present / reachable). */
    boolean isAvailable();

    String providerName();

    record Request(
            String systemPrompt,
            List<Turn> history,
            String userPrompt,
            String contextJson,
            int maxTokens
    ) {}

    record Turn(String role, String content) {}   // role: "user" | "assistant"

    record Response(String text, String model, Integer promptTokens, Integer completionTokens, Map<String, Object> raw) {}
}
