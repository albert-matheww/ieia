package com.ieia.integration.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The default provider must be deterministic and copy numbers verbatim. */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    private LlmClient.Response ask(String question, String contextJson) {
        return client.complete(new LlmClient.Request(
                "You answer only from CONTEXT.", List.of(), question, contextJson, 400));
    }

    @Test
    void explains_a_score_drop_from_the_drivers_in_context() {
        String ctx = """
            {"period":"2026-06","scoreDelta":-8.5,"dataWindow":"2026-01..2026-06",
             "drivers":[{"category":"carbon","changePct":18,"pointsImpact":-4.6,"direction":"WORSENED"},
                        {"category":"transport","changePct":11,"pointsImpact":-1.8,"direction":"WORSENED"}]}""";
        String text = ask("Why did my environmental score decrease?", ctx).text();
        assertThat(text).contains("decreased by 8.5 points");
        assertThat(text).contains("Carbon").contains("18%");
        assertThat(text).contains("2026-01..2026-06");   // data-window disclaimer
    }

    @Test
    void names_the_largest_contributor_from_context() {
        String ctx = """
            {"topContributors":[{"category":"ELECTRICITY","pct":46,"co2eKg":410},
                                {"category":"TRANSPORT","pct":22}]}""";
        String text = ask("What is causing most of my emissions?", ctx).text();
        assertThat(text).contains("electricity").contains("46%").contains("410");
    }

    @Test
    void finds_the_highest_emission_month() {
        String ctx = """
            {"metrics12mo":[{"period":"2026-01","co2eKg":800},{"period":"2026-02","co2eKg":1200},
                            {"period":"2026-03","co2eKg":900}]}""";
        String text = ask("Which month had the highest emissions?", ctx).text();
        assertThat(text).contains("2026-02").contains("1200");
    }

    @Test
    void admits_when_it_has_no_data() {
        String text = ask("Give me a summary of my environmental performance.", "{}").text();
        assertThat(text.toLowerCase()).contains("don't have enough recorded data");
    }

    @Test
    void is_deterministic() {
        String ctx = "{\"score\":72,\"period\":\"2026-06\"}";
        assertThat(ask("summary", ctx).text()).isEqualTo(ask("summary", ctx).text());
    }

    @Test
    void rephrase_mode_preserves_the_recommendation_numbers() {
        String ctx = """
            {"items":[{"rank":1,"title":"Reduce electricity by 15%",
                       "rationale":"Would save 68 kg CO2e per month"}]}""";
        String out = client.complete(new LlmClient.Request(
                "REPHRASE_RECOMMENDATIONS", List.of(), "rewrite", ctx, 300)).text();
        assertThat(out).contains("68").contains("15");
        assertThat(out).startsWith("[");   // JSON array
    }
}
