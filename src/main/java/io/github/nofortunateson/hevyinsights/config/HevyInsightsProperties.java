package io.github.nofortunateson.hevyinsights.config;

import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of this application (not of Hevy).
 *
 * @param userId UUID of the single user whose data is stored (ADR 0008, D1); every table row carries it
 */
@ConfigurationProperties("hevy-insights")
public record HevyInsightsProperties(UUID userId) {

    public HevyInsightsProperties {
        Objects.requireNonNull(userId, "hevy-insights.user-id must be set");
    }
}
