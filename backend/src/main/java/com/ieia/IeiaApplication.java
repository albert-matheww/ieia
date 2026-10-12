package com.ieia;

import com.ieia.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Intelligent Environmental Impact Analyzer — backend entry point.
 *
 * <p>Responsibilities of this service (see docs/01-architecture.md):
 * authentication & RBAC, environmental-record CRUD, the impact calculation
 * engine, metric aggregation, transparent scoring, and orchestration of the
 * stateless ML service and the pluggable LLM provider.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
@EnableScheduling
public class IeiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(IeiaApplication.class, args);
    }
}
