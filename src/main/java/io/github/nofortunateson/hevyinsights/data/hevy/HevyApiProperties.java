package io.github.nofortunateson.hevyinsights.data.hevy;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Hevy API.
 *
 * @param baseUrl              Hevy API root
 * @param key                  the user's API key (from {@code HEVY_API_KEY}); a secret, never logged
 * @param maxRateLimitAttempts total attempts per request while Hevy answers HTTP 429
 */
@ConfigurationProperties("hevy.api")
public record HevyApiProperties(
        @DefaultValue("https://api.hevyapp.com") URI baseUrl,
        String key,
        @DefaultValue("5") int maxRateLimitAttempts) {

    public HevyApiProperties {
        if (maxRateLimitAttempts < 1) {
            throw new IllegalArgumentException("hevy.api.max-rate-limit-attempts must be at least 1");
        }
    }

    boolean hasKey() {
        return key != null && !key.isBlank();
    }

    /** Masks the key: records print every component by default. */
    @Override
    public String toString() {
        return "HevyApiProperties[baseUrl=" + baseUrl + ", key=" + (hasKey() ? "***" : "<missing>")
                + ", maxRateLimitAttempts=" + maxRateLimitAttempts + "]";
    }
}
