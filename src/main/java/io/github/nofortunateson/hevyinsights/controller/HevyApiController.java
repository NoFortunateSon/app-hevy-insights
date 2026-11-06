package io.github.nofortunateson.hevyinsights.controller;

import static io.github.nofortunateson.hevyinsights.utils.Constants.HEVY_WORKOUTS_PATH;
import static io.github.nofortunateson.hevyinsights.utils.Constants.HEVY_WORKOUT_EVENTS_PATH;

import io.github.nofortunateson.hevyinsights.controller.hevy.HevyHttpClient;
import io.github.nofortunateson.hevyinsights.controller.hevy.HevyPageWalker;
import io.github.nofortunateson.hevyinsights.controller.hevy.HevyResponseMapper;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutDto;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutEventDto;
import io.github.nofortunateson.hevyinsights.utils.RateLimitRetryPolicy;
import io.github.nofortunateson.hevyinsights.utils.RetrySleeper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Read-only access to the Hevy public API: the entry point of {@code controller/hevy}. Only GET requests.
 * <p>
 * Callers get complete lists: pages are walked and HTTP 429 is retried inside. The work is split by task
 * (ADR 0007, addendum): {@link HevyHttpClient} sends the requests, {@link HevyPageWalker} walks the pages,
 * {@link HevyResponseMapper} reads the JSON, {@link RateLimitRetryPolicy} decides about 429.
 */
public class HevyApiController {

    private final HevyPageWalker pageWalker;
    private final HevyResponseMapper responseMapper = new HevyResponseMapper();

    public HevyApiController(RestClient restClient, RetrySleeper sleeper, int maxRateLimitAttempts) {
        RateLimitRetryPolicy retryPolicy = new RateLimitRetryPolicy(maxRateLimitAttempts);
        this.pageWalker = new HevyPageWalker(new HevyHttpClient(restClient, sleeper, retryPolicy));
    }

    /** Every workout of the user, all pages. */
    public List<HevyWorkoutDto> fetchAllWorkouts() {
        return pageWalker.fetchAll(HEVY_WORKOUTS_PATH, Map.of(), responseMapper::workoutsPage);
    }

    /**
     * Workout updates and deletions since the given time, all pages, in the order Hevy returns
     * them (documented as newest first).
     */
    public List<HevyWorkoutEventDto> fetchWorkoutEventsSince(Instant since) {
        return pageWalker.fetchAll(HEVY_WORKOUT_EVENTS_PATH, Map.of("since", since.toString()),
                responseMapper::workoutEventsPage);
    }
}
