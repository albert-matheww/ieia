package com.ieia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Strongly-typed binding for every {@code ieia.*} setting in application.yml.
 * Nothing security-relevant is hard-coded; all of this is environment-driven.
 */
@ConfigurationProperties(prefix = "ieia")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Ml ml = new Ml();
    private final Llm llm = new Llm();
    private final Seed seed = new Seed();
    private final Reports reports = new Reports();

    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }
    public Ml getMl() { return ml; }
    public Llm getLlm() { return llm; }
    public Seed getSeed() { return seed; }
    public Reports getReports() { return reports; }

    // ---------------------------------------------------------------------

    public static class Jwt {
        /** HS256 signing secret — must be >= 32 bytes of entropy in real use. */
        private String secret;
        private long accessTtlSeconds = 900;
        private long refreshTtlSeconds = 604800;
        private String issuer = "ieia";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTtlSeconds() { return accessTtlSeconds; }
        public void setAccessTtlSeconds(long v) { this.accessTtlSeconds = v; }
        public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
        public void setRefreshTtlSeconds(long v) { this.refreshTtlSeconds = v; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
    }

    public static class Cors {
        private String allowedOrigin = "http://localhost:5173";
        public String getAllowedOrigin() { return allowedOrigin; }
        public void setAllowedOrigin(String v) { this.allowedOrigin = v; }
    }

    public static class Ml {
        private String baseUrl = "http://localhost:8000";
        private String token = "dev-ml-token";
        private int timeoutMs = 8000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getToken() { return token; }
        public void setToken(String v) { this.token = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
    }

    public static class Llm {
        /** MOCK | OPENAI | ANTHROPIC | OLLAMA */
        private String provider = "MOCK";
        private String model = "mock-analyst-v1";
        private int timeoutMs = 20000;
        private int maxTokens = 800;
        @NestedConfigurationProperty
        private final Provider openai = new Provider("https://api.openai.com/v1");
        @NestedConfigurationProperty
        private final Provider anthropic = new Provider("https://api.anthropic.com");
        @NestedConfigurationProperty
        private final Provider ollama = new Provider("http://localhost:11434");

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int v) { this.maxTokens = v; }
        public Provider getOpenai() { return openai; }
        public Provider getAnthropic() { return anthropic; }
        public Provider getOllama() { return ollama; }

        public static class Provider {
            private String apiKey = "";
            private String baseUrl;
            public Provider() {}
            public Provider(String baseUrl) { this.baseUrl = baseUrl; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String v) { this.apiKey = v; }
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String v) { this.baseUrl = v; }
        }
    }

    public static class Seed {
        private boolean demo = true;
        public boolean isDemo() { return demo; }
        public void setDemo(boolean demo) { this.demo = demo; }
    }

    public static class Reports {
        private String outputDir = "./reports-output";
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String v) { this.outputDir = v; }
    }
}
