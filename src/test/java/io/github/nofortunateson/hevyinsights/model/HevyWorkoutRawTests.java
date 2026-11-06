package io.github.nofortunateson.hevyinsights.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules of a stored Hevy workout (ADR 0008), without database. Data is invented. */
class HevyWorkoutRawTests {

    private static final HevyWorkoutRawId ID = new HevyWorkoutRawId(UUID.randomUUID(), "w-1");
    private static final Instant STORED_VERSION = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant FIRST_SYNC = Instant.parse("2026-11-01T11:00:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-11-01T17:00:00Z");

    private final HevyWorkoutRaw workout = new HevyWorkoutRaw(ID, "{\"v\":1}", STORED_VERSION, FIRST_SYNC);

    @Test
    void newerVersionReplacesTheStoredOne() {
        Instant newer = STORED_VERSION.plusSeconds(60);

        boolean changed = workout.updateFromHevy("{\"v\":2}", newer, SECOND_SYNC);

        assertThat(changed).isTrue();
        assertThat(workout.payload()).isEqualTo("{\"v\":2}");
        assertThat(workout.hevyUpdatedAt()).isEqualTo(newer);
        assertThat(workout.syncedAt()).isEqualTo(SECOND_SYNC);
    }

    @Test
    void sameVersionIsStoredAgain() {
        assertThat(workout.updateFromHevy("{\"v\":1}", STORED_VERSION, SECOND_SYNC)).isTrue();
        assertThat(workout.syncedAt()).isEqualTo(SECOND_SYNC);
    }

    @Test
    void olderVersionIsIgnored() {
        boolean changed = workout.updateFromHevy("{\"v\":0}", STORED_VERSION.minusSeconds(1), SECOND_SYNC);

        assertThat(changed).isFalse();
        assertThat(workout.payload()).isEqualTo("{\"v\":1}");
        assertThat(workout.hevyUpdatedAt()).isEqualTo(STORED_VERSION);
        assertThat(workout.syncedAt()).isEqualTo(FIRST_SYNC);
    }

    @Test
    void newWorkoutIsNotDeleted() {
        assertThat(workout.deletedAt()).isNull();
        assertThat(workout.id()).isEqualTo(ID);
    }

    @Test
    void deletionKeepsTheFirstDeletionTime() {
        workout.markDeleted(FIRST_SYNC);
        workout.markDeleted(SECOND_SYNC);

        assertThat(workout.deletedAt()).isEqualTo(FIRST_SYNC);
    }

    @Test
    void updateAfterDeletionBringsTheWorkoutBack() {
        workout.markDeleted(FIRST_SYNC);

        workout.updateFromHevy("{\"v\":2}", STORED_VERSION.plusSeconds(60), SECOND_SYNC);

        assertThat(workout.deletedAt()).isNull();
    }

    @Test
    void olderUpdateDoesNotUndoADeletion() {
        workout.markDeleted(FIRST_SYNC);

        workout.updateFromHevy("{\"v\":0}", STORED_VERSION.minusSeconds(1), SECOND_SYNC);

        assertThat(workout.deletedAt()).isEqualTo(FIRST_SYNC);
    }
}
