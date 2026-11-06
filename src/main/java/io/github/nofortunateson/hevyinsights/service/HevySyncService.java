package io.github.nofortunateson.hevyinsights.service;

import io.github.nofortunateson.hevyinsights.config.HevyInsightsProperties;
import io.github.nofortunateson.hevyinsights.model.HevySyncState;
import io.github.nofortunateson.hevyinsights.model.HevyWorkoutRaw;
import io.github.nofortunateson.hevyinsights.model.HevyWorkoutRawId;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutDto;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutEventDto;
import io.github.nofortunateson.hevyinsights.repository.HevySyncStateRepository;
import io.github.nofortunateson.hevyinsights.repository.HevyWorkoutRawRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the local copy of the user's Hevy workouts up to date (ADR 0008). The controller reads from Hevy and
 * hands the data over; this service decides what to store and moves the restart point only when a whole
 * batch is stored.
 */
@Service
public class HevySyncService {

    private final HevyWorkoutRawRepository workouts;
    private final HevySyncStateRepository syncStates;
    private final Clock clock;
    private final UUID userId;

    public HevySyncService(HevyWorkoutRawRepository workouts, HevySyncStateRepository syncStates, Clock clock,
            HevyInsightsProperties properties) {
        this.workouts = workouts;
        this.syncStates = syncStates;
        this.clock = clock;
        this.userId = properties.userId();
    }

    /** Start time of the last successful sync; empty before the first one, which must be a full load. */
    @Transactional(readOnly = true)
    public Optional<Instant> lastSyncStart() {
        return syncStates.findById(userId).map(HevySyncState::lastSyncStartedAt);
    }

    /** First sync (A1): every workout Hevy has, then the restart point. */
    @Transactional
    public void storeFullLoad(List<HevyWorkoutDto> allWorkouts, Instant syncStartedAt) {
        allWorkouts.forEach(this::store);
        recordSuccessfulSync(syncStartedAt);
    }

    /**
     * Later syncs: Hevy's events since the restart point. Hevy lists them newest first, so they are applied
     * in reverse: when one workout has several events, the newest decides its final state.
     */
    @Transactional
    public void applyEvents(List<HevyWorkoutEventDto> eventsNewestFirst, Instant syncStartedAt) {
        eventsNewestFirst.reversed().forEach(event -> apply(event, syncStartedAt));
        recordSuccessfulSync(syncStartedAt);
    }

    private void apply(HevyWorkoutEventDto event, Instant syncStartedAt) {
        switch (event) {
            case HevyWorkoutEventDto.Updated updated -> store(updated.workout());
            case HevyWorkoutEventDto.Deleted deleted -> markDeleted(deleted, syncStartedAt);
        }
    }

    private void store(HevyWorkoutDto workout) {
        HevyWorkoutRawId id = new HevyWorkoutRawId(userId, workout.id());
        Instant now = clock.instant();
        workouts.findById(id).ifPresentOrElse(
                existing -> existing.updateFromHevy(workout.rawJson(), workout.updatedAt(), now),
                () -> workouts.save(new HevyWorkoutRaw(id, workout.rawJson(), workout.updatedAt(), now)));
    }

    /**
     * A deletion of a workout we never stored needs no row. When Hevy omits {@code deleted_at}, the start of
     * this sync is the best time we know.
     */
    private void markDeleted(HevyWorkoutEventDto.Deleted deleted, Instant syncStartedAt) {
        Instant deletedAt = deleted.deletedAt() != null ? deleted.deletedAt() : syncStartedAt;
        workouts.findById(new HevyWorkoutRawId(userId, deleted.workoutId()))
                .ifPresent(workout -> workout.markDeleted(deletedAt));
    }

    private void recordSuccessfulSync(Instant syncStartedAt) {
        syncStates.findById(userId).ifPresentOrElse(
                state -> state.recordSuccessfulSync(syncStartedAt),
                () -> syncStates.save(new HevySyncState(userId, syncStartedAt)));
    }
}
