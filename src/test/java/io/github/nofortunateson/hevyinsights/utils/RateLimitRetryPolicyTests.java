package io.github.nofortunateson.hevyinsights.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitRetryPolicyTests {

    private final RateLimitRetryPolicy policy = new RateLimitRetryPolicy(3);

    @Test
    void retriesUntilTheAttemptsRunOut() {
        assertThat(policy.canRetryAfter(1)).isTrue();
        assertThat(policy.canRetryAfter(2)).isTrue();
        assertThat(policy.canRetryAfter(3)).isFalse();
    }

    @Test
    void singleAttemptNeverRetries() {
        assertThat(new RateLimitRetryPolicy(1).canRetryAfter(1)).isFalse();
    }

    @Test
    void retryAfterInSecondsWins() {
        assertThat(policy.delayAfter("7", 1)).isEqualTo(Duration.ofSeconds(7));
        assertThat(policy.delayAfter(" 0 ", 4)).isEqualTo(Duration.ZERO);
    }

    @Test
    void retryAfterIsCappedAtTheMaximumBackoff() {
        assertThat(policy.delayAfter("3600", 1)).isEqualTo(Constants.HEVY_MAX_BACKOFF);
    }

    @Test
    void withoutUsableRetryAfterTheWaitDoublesFromTheInitialBackoff() {
        assertThat(policy.delayAfter(null, 1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayAfter(null, 2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayAfter(null, 3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayAfter("Wed, 21 Oct 2026 07:28:00 GMT", 2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayAfter("-5", 1)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void backoffIsCappedAtTheMaximum() {
        assertThat(policy.delayAfter(null, 30)).isEqualTo(Constants.HEVY_MAX_BACKOFF);
    }
}
