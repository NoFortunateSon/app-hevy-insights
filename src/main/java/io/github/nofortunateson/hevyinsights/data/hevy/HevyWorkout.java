package io.github.nofortunateson.hevyinsights.data.hevy;

import java.time.Instant;
import java.util.Objects;

/**
 * A workout as returned by Hevy.
 *
 * @param id        Hevy workout id
 * @param updatedAt last update time reported by Hevy
 * @param rawJson   the original workout JSON object, kept so callers can store it unchanged
 */
public record HevyWorkout(String id, Instant updatedAt, String rawJson) {

    public HevyWorkout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(rawJson, "rawJson");
    }
}
