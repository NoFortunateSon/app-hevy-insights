-- Original Hevy workout JSON, one row per workout and user (ADR 0005).
-- Written by the sync module; normalised tables are derived from it later.
CREATE TABLE hevy_workout_raw (
    user_id          UUID        NOT NULL,
    hevy_workout_id  TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    hevy_updated_at  TIMESTAMPTZ NOT NULL,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, hevy_workout_id)
);
