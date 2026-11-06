package io.github.nofortunateson.hevyinsights.data.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nofortunateson.hevyinsights.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks migration V1 on a real Postgres: the table exists and its constraints hold. Data is invented. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HevyWorkoutRawSchemaTests {

    private static final String INSERT = """
            INSERT INTO hevy_workout_raw (user_id, hevy_workout_id, payload, hevy_updated_at)
            VALUES (?, ?, CAST(? AS jsonb), ?)
            """;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTable() {
        jdbc.sql("DELETE FROM hevy_workout_raw").update();
    }

    @Test
    void flywayAppliedVersionOne() {
        String version = jdbc.sql("""
                SELECT version FROM flyway_schema_history
                WHERE success ORDER BY installed_rank DESC LIMIT 1
                """).query(String.class).single();

        assertThat(version).isEqualTo("1");
    }

    @Test
    void storesPayloadAsQueryableJsonAndDefaultsSyncedAt() {
        UUID userId = UUID.randomUUID();
        insert(userId, "w-1", "{\"title\": \"Push day\", \"exercises\": []}");

        String title = jdbc.sql("""
                SELECT payload ->> 'title' FROM hevy_workout_raw
                WHERE user_id = ? AND hevy_workout_id = ?
                """).params(userId, "w-1").query(String.class).single();
        Boolean syncedAtSet = jdbc.sql("SELECT synced_at IS NOT NULL FROM hevy_workout_raw")
                .query(Boolean.class).single();

        assertThat(title).isEqualTo("Push day");
        assertThat(syncedAtSet).isTrue();
    }

    @Test
    void sameWorkoutIdIsAllowedForDifferentUsers() {
        insert(UUID.randomUUID(), "w-1", "{}");
        insert(UUID.randomUUID(), "w-1", "{}");

        assertThat(jdbc.sql("SELECT count(*) FROM hevy_workout_raw").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateWorkoutForSameUser() {
        UUID userId = UUID.randomUUID();
        insert(userId, "w-1", "{}");

        assertThatThrownBy(() -> insert(userId, "w-1", "{}")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsMissingUserId() {
        assertThatThrownBy(() -> insert(null, "w-1", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> insert(UUID.randomUUID(), "w-1", "not json"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(UUID userId, String workoutId, String json) {
        jdbc.sql(INSERT).params(userId, workoutId, json, OffsetDateTime.parse("2026-11-01T10:00:00Z")).update();
    }
}
