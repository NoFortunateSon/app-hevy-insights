package io.github.nofortunateson.hevyinsights.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-wide settings: the properties of this application and its clock (UTC, replaceable in tests).
 * Hevy-specific settings live in {@link HevyApiConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HevyInsightsProperties.class)
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
