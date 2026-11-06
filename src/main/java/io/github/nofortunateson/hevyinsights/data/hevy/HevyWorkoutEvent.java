package io.github.nofortunateson.hevyinsights.data.hevy;

import java.time.Instant;
import java.util.Objects;

/** A change to a workout reported by Hevy's workout events endpoint. */
public sealed interface HevyWorkoutEvent {

    /** The workout was created or updated; carries its current state. */
    record Updated(HevyWorkout workout) implements HevyWorkoutEvent {
        public Updated {
            Objects.requireNonNull(workout, "workout");
        }
    }

    /**
     * The workout was deleted on Hevy.
     *
     * @param deletedAt deletion time, or {@code null} because Hevy does not mark it as required
     */
    record Deleted(String workoutId, Instant deletedAt) implements HevyWorkoutEvent {
        public Deleted {
            Objects.requireNonNull(workoutId, "workoutId");
        }
    }
}
