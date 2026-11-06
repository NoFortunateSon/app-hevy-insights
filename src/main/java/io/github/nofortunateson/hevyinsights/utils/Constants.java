package io.github.nofortunateson.hevyinsights.utils;

import java.time.Duration;

/** Fixed values of the application. Values that change per environment go in {@code application.yaml}. */
public final class Constants {

    /** Header name documented by Hevy for the user's API key. */
    public static final String HEVY_API_KEY_HEADER = "api-key";

    /** Maximum page size allowed by Hevy for workouts and workout events. */
    public static final int HEVY_PAGE_SIZE = 10;

    public static final String HEVY_WORKOUTS_PATH = "/v1/workouts";
    public static final String HEVY_WORKOUT_EVENTS_PATH = "/v1/workouts/events";

    /**
     * Wait before the first retry after HTTP 429 without {@code Retry-After}; it doubles at each attempt
     * up to {@link #HEVY_MAX_BACKOFF}. Hevy does not document its rate limits: defensive guesses.
     */
    public static final Duration HEVY_INITIAL_BACKOFF = Duration.ofSeconds(1);
    public static final Duration HEVY_MAX_BACKOFF = Duration.ofSeconds(60);

    private Constants() {
    }
}
