package com.ieia.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that need a real PostgreSQL (the schema uses jsonb,
 * plpgsql triggers, partial indexes and {@code gen_random_uuid()}, so an
 * in-memory DB is not an option). Requires a running Docker daemon.
 *
 * <p>One container is shared across the whole test run (static, not closed —
 * Ryuk / the JVM shutdown hook reaps it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ieia_test")
                    .withUsername("ieia")
                    .withPassword("ieia")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ieia.jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret-0123456789");
        registry.add("ieia.seed.demo", () -> "false");
        registry.add("ieia.llm.provider", () -> "MOCK");
    }
}
