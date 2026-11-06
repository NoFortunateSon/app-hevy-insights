package io.github.nofortunateson.hevyinsights.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** Validation of the Hevy settings. The key is invented. */
class HevyApiPropertiesTests {

    private static final URI BASE = URI.create("https://hevy.test");

    @Test
    void oneAttemptIsTheMinimum() {
        assertThat(new HevyApiProperties(BASE, "k", 1).maxRateLimitAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> new HevyApiProperties(BASE, "k", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hevy.api.max-rate-limit-attempts must be at least 1");
    }

    @Test
    void blankOrMissingKeyCountsAsNoKey() {
        assertThat(new HevyApiProperties(BASE, "k", 1).hasKey()).isTrue();
        assertThat(new HevyApiProperties(BASE, " ", 1).hasKey()).isFalse();
        assertThat(new HevyApiProperties(BASE, null, 1).hasKey()).isFalse();
    }

    @Test
    void textNeverContainsTheKey() {
        assertThat(new HevyApiProperties(BASE, "secret-value", 1).toString())
                .doesNotContain("secret-value").contains("key=***");
        assertThat(new HevyApiProperties(BASE, null, 1).toString()).contains("key=<missing>");
    }
}
