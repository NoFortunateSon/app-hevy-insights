package io.github.nofortunateson.hevyinsights.data.hevy;

import org.springframework.http.HttpStatus;

/** Hevy kept answering HTTP 429 until the configured number of attempts ran out. */
public class HevyRateLimitedException extends HevyApiException {

    HevyRateLimitedException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
