package io.github.nofortunateson.hevyinsights.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled Hevy sync (ADR 0008, E1 + E2). Off when {@code hevy-insights.sync.scheduled=false},
 * which the test run sets so that no test reaches Hevy.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "hevy-insights.sync.scheduled", havingValue = "true", matchIfMissing = true)
public class HevySyncSchedulingConfiguration {
}
