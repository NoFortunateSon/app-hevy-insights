package io.github.nofortunateson.hevyinsights.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Where the next sync of a user starts (table {@code hevy_sync_state}, ADR 0008 B1): the start time of the
 * last successful sync. Changes made on Hevy during a sync are read again the next time, on purpose.
 */
@Entity
@Table(name = "hevy_sync_state")
public class HevySyncState {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_sync_started_at", nullable = false)
    private Instant lastSyncStartedAt;

    /** For JPA only. */
    protected HevySyncState() {
    }

    public HevySyncState(UUID userId, Instant lastSyncStartedAt) {
        this.userId = userId;
        this.lastSyncStartedAt = lastSyncStartedAt;
    }

    public void recordSuccessfulSync(Instant startedAt) {
        this.lastSyncStartedAt = startedAt;
    }

    public Instant lastSyncStartedAt() {
        return lastSyncStartedAt;
    }
}
