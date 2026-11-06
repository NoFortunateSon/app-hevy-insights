-- Sync design (ADR 0008): soft delete of workouts deleted on Hevy (C1) and the restart point (B1).

-- Set when Hevy reports the workout as deleted; NULL while it exists. Metrics ignore deleted rows.
ALTER TABLE hevy_workout_raw ADD COLUMN deleted_at TIMESTAMPTZ;

-- One row per user: start time of the last successful sync. The next sync asks Hevy for events since then.
CREATE TABLE hevy_sync_state (
    user_id               UUID        PRIMARY KEY,
    last_sync_started_at  TIMESTAMPTZ NOT NULL
);
