package io.github.nofortunateson.hevyinsights.hevyclient;

/** Hevy kept answering HTTP 429 until the configured number of attempts ran out. */
public class HevyRateLimitedException extends HevyApiException {

    HevyRateLimitedException(String message) {
        super(message, 429);
    }
}
