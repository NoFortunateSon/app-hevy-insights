package io.github.nofortunateson.hevyinsights.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/** Primary key of {@link HevyWorkoutRaw}: the same Hevy workout id may exist for different users (ADR 0005). */
@Embeddable
public record HevyWorkoutRawId(
        @Column(name = "user_id") UUID userId,
        @Column(name = "hevy_workout_id") String hevyWorkoutId) {

    public HevyWorkoutRawId {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(hevyWorkoutId, "hevyWorkoutId");
    }
}
