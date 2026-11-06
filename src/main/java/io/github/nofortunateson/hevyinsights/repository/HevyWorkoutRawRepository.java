package io.github.nofortunateson.hevyinsights.repository;

import io.github.nofortunateson.hevyinsights.model.HevyWorkoutRaw;
import io.github.nofortunateson.hevyinsights.model.HevyWorkoutRawId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Table {@code hevy_workout_raw}. */
public interface HevyWorkoutRawRepository extends JpaRepository<HevyWorkoutRaw, HevyWorkoutRawId> {
}
