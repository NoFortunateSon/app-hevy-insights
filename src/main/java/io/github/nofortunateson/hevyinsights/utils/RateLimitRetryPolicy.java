package io.github.nofortunateson.hevyinsights.utils;

import static io.github.nofortunateson.hevyinsights.utils.Constants.HEVY_INITIAL_BACKOFF;
import static io.github.nofortunateson.hevyinsights.utils.Constants.HEVY_MAX_BACKOFF;

import java.time.Duration;
import java.util.Optional;

/**
 * Decides whether to retry after HTTP 429 and how long to wait first. Pure logic: it neither sends requests
 * nor sleeps.
 * <p>
 * Waits for {@code Retry-After} in seconds when present, otherwise backs off exponentially; both capped.
 * Hevy does not document its rate limits, so these choices are defensive guesses.
 */
public final class RateLimitRetryPolicy {

    private final int maxAttempts;

    /** @param maxAttempts total attempts per request, the first one included (validated by {@code HevyApiProperties}) */
    public RateLimitRetryPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /** Whether another attempt is allowed after {@code attemptsDone} answers of HTTP 429. */
    public boolean canRetryAfter(int attemptsDone) {
        return attemptsDone < maxAttempts;
    }

    /**
     * Wait before the next attempt.
     *
     * @param retryAfter   the {@code Retry-After} header of the 429 answer, or {@code null}
     * @param attemptsDone attempts already made, at least 1
     */
    public Duration delayAfter(String retryAfter, int attemptsDone) {
        return retryAfterSeconds(retryAfter).orElseGet(() -> exponentialBackoff(attemptsDone));
    }

    /** Only the delta-seconds form; the HTTP-date form or garbage yields empty. */
    private static Optional<Duration> retryAfterSeconds(String retryAfter) {
        if (retryAfter == null) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds < 0 ? Optional.empty() : Optional.of(capped(Duration.ofSeconds(seconds)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Duration exponentialBackoff(int attemptsDone) {
        Duration backoff = HEVY_INITIAL_BACKOFF;
        for (int doubling = 1; doubling < attemptsDone && backoff.compareTo(HEVY_MAX_BACKOFF) < 0; doubling++) {
            backoff = backoff.multipliedBy(2);
        }
        return capped(backoff);
    }

    private static Duration capped(Duration delay) {
        return delay.compareTo(HEVY_MAX_BACKOFF) > 0 ? HEVY_MAX_BACKOFF : delay;
    }
}
