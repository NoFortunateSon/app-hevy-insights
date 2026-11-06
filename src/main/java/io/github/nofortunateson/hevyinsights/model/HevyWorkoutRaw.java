package io.github.nofortunateson.hevyinsights.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Hevy workout as stored by us: the original JSON plus the times we need for the sync (table
 * {@code hevy_workout_raw}, ADR 0005 and 0008).
 * <p>
 * Rules: an older version never replaces a newer one; a workout deleted on Hevy is only marked
 * ({@code deleted_at}), and comes back if Hevy reports it updated again.
 */
@Entity
@Table(name = "hevy_workout_raw")
public class HevyWorkoutRaw {

    @EmbeddedId
    private HevyWorkoutRawId id;

    /** Original Hevy JSON, stored as {@code JSONB}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "hevy_updated_at", nullable = false)
    private Instant hevyUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** For JPA only. */
    protected HevyWorkoutRaw() {
    }

    public HevyWorkoutRaw(HevyWorkoutRawId id, String payload, Instant hevyUpdatedAt, Instant syncedAt) {
        this.id = id;
        this.payload = payload;
        this.hevyUpdatedAt = hevyUpdatedAt;
        this.syncedAt = syncedAt;
    }

    /**
     * Replaces the stored version with the one from Hevy, unless the stored one is newer.
     *
     * @return whether the row changed
     */
    public boolean updateFromHevy(String newPayload, Instant newHevyUpdatedAt, Instant now) {
        if (hevyUpdatedAt.isAfter(newHevyUpdatedAt)) {
            return false;
        }
        this.payload = newPayload;
        this.hevyUpdatedAt = newHevyUpdatedAt;
        this.syncedAt = now;
        this.deletedAt = null;
        return true;
    }

    /** Marks the workout as deleted on Hevy; the first deletion time is kept. */
    public void markDeleted(Instant at) {
        if (deletedAt == null) {
            deletedAt = at;
        }
    }

    public HevyWorkoutRawId id() {
        return id;
    }

    public String payload() {
        return payload;
    }

    public Instant hevyUpdatedAt() {
        return hevyUpdatedAt;
    }

    public Instant syncedAt() {
        return syncedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
