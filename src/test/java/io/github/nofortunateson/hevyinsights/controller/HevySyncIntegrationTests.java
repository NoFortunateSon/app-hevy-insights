package io.github.nofortunateson.hevyinsights.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.nofortunateson.hevyinsights.TestcontainersConfiguration;
import io.github.nofortunateson.hevyinsights.config.HevyApiConfiguration;
import io.github.nofortunateson.hevyinsights.config.HevyApiProperties;
import io.github.nofortunateson.hevyinsights.config.HevyInsightsProperties;
import io.github.nofortunateson.hevyinsights.exception.HevyApiException;
import io.github.nofortunateson.hevyinsights.service.HevySyncService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The whole sync (ADR 0008) on a real Postgres, with recorded-shape Hevy responses (invented data). No real
 * Hevy call: requests go to {@link MockRestServiceServer}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HevySyncIntegrationTests {

    private static final String BASE = "https://hevy.test";
    // Not a real key: an all-zero UUID, the format Hevy documents for the header.
    private static final String FAKE_KEY = "00000000-0000-0000-0000-000000000000";
    private static final int ATTEMPTS = 3;
    private static final Instant FIRST_SYNC = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-11-01T16:00:00Z");

    @Autowired
    private HevySyncService syncService;

    @Autowired
    private HevyInsightsProperties properties;

    @Autowired
    private JdbcClient jdbc;

    private MockRestServiceServer server;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("DELETE FROM hevy_workout_raw").update();
        jdbc.sql("DELETE FROM hevy_sync_state").update();
    }

    @Test
    void firstSyncLoadsEveryWorkoutAndRecordsItsStart() {
        HevySyncController sync = syncAt(FIRST_SYNC, FAKE_KEY);
        expectWorkoutPages();

        sync.synchronize();

        server.verify();
        assertThat(jdbc.sql("SELECT hevy_workout_id FROM hevy_workout_raw ORDER BY 1").query(String.class).list())
                .containsExactly("w-0001", "w-0002");
        assertThat(jdbc.sql("SELECT payload ->> 'title' FROM hevy_workout_raw WHERE hevy_workout_id = 'w-0001'")
                .query(String.class).single()).isEqualTo("Invented push day");
        assertThat(lastSyncStart()).contains(FIRST_SYNC);
    }

    @Test
    void laterSyncAppliesTheEventsSinceTheLastStart() {
        HevySyncController first = syncAt(FIRST_SYNC, FAKE_KEY);
        expectWorkoutPages();
        first.synchronize();

        HevySyncController second = syncAt(SECOND_SYNC, FAKE_KEY);
        server.expect(requestTo(eventsUrl(FIRST_SYNC)))
                .andRespond(withSuccess(fixture("workout-events-page-1.json"), MediaType.APPLICATION_JSON));
        second.synchronize();

        server.verify();
        assertThat(hevyUpdatedAt("w-0002")).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"));
        assertThat(deletedAt("w-0002")).isEmpty();
        assertThat(deletedAt("w-0001")).contains(Instant.parse("2026-10-02T12:00:00Z"));
        assertThat(jdbc.sql("SELECT count(*) FROM hevy_workout_raw WHERE hevy_workout_id = 'w-0000'")
                .query(Long.class).single()).as("deletion of a workout never stored").isZero();
        assertThat(lastSyncStart()).contains(SECOND_SYNC);
    }

    @Test
    void newestEventDecidesWhenAWorkoutHasSeveral() {
        recordLastSyncStart(FIRST_SYNC);
        HevySyncController sync = syncAt(SECOND_SYNC, FAKE_KEY);
        // Newest first, as Hevy lists them: w-1 deleted after its update; w-2 updated again after a deletion.
        server.expect(requestTo(eventsUrl(FIRST_SYNC))).andRespond(withSuccess("""
                {"page": 1, "page_count": 1, "events": [
                  {"type": "updated", "workout": {"id": "w-2", "updated_at": "2026-11-01T12:00:00Z"}},
                  {"type": "deleted", "id": "w-1", "deleted_at": "2026-11-01T11:30:00Z"},
                  {"type": "deleted", "id": "w-2", "deleted_at": "2026-11-01T11:00:00Z"},
                  {"type": "updated", "workout": {"id": "w-1", "updated_at": "2026-11-01T10:30:00Z"}},
                  {"type": "updated", "workout": {"id": "w-2", "updated_at": "2026-11-01T10:15:00Z"}}
                ]}
                """, MediaType.APPLICATION_JSON));

        sync.synchronize();

        assertThat(deletedAt("w-1")).contains(Instant.parse("2026-11-01T11:30:00Z"));
        assertThat(deletedAt("w-2")).isEmpty();
        assertThat(hevyUpdatedAt("w-2")).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
    }

    @Test
    void olderVersionNeverReplacesANewerOne() {
        recordLastSyncStart(FIRST_SYNC);
        insertWorkout("w-1", "{\"title\": \"new\"}", Instant.parse("2026-11-01T12:00:00Z"));
        HevySyncController sync = syncAt(SECOND_SYNC, FAKE_KEY);
        server.expect(requestTo(eventsUrl(FIRST_SYNC))).andRespond(withSuccess("""
                {"page_count": 1, "events": [
                  {"type": "updated", "workout": {"id": "w-1", "updated_at": "2026-11-01T09:00:00Z", "title": "old"}}
                ]}
                """, MediaType.APPLICATION_JSON));

        sync.synchronize();

        assertThat(jdbc.sql("SELECT payload ->> 'title' FROM hevy_workout_raw WHERE hevy_workout_id = 'w-1'")
                .query(String.class).single()).isEqualTo("new");
    }

    @Test
    void failedHevyCallKeepsTheRestartPoint() {
        recordLastSyncStart(FIRST_SYNC);
        HevySyncController sync = syncAt(SECOND_SYNC, FAKE_KEY);
        server.expect(requestTo(eventsUrl(FIRST_SYNC))).andRespond(withServerError());

        assertThatThrownBy(sync::synchronize).isInstanceOf(HevyApiException.class);
        assertThat(lastSyncStart()).contains(FIRST_SYNC);
    }

    @Test
    void withoutApiKeyNothingIsCalledOrStored() {
        HevySyncController sync = syncAt(FIRST_SYNC, " ");
        server.expect(never(), anything());

        sync.synchronize();

        server.verify();
        assertThat(lastSyncStart()).isEmpty();
    }

    /** The real controller and service, with Hevy replaced by the mock server and a fixed clock. */
    private HevySyncController syncAt(Instant now, String apiKey) {
        HevyApiProperties hevyProperties = new HevyApiProperties(URI.create(BASE), apiKey, ATTEMPTS);
        RestClient.Builder builder = HevyApiConfiguration.restClientBuilder(hevyProperties);
        server = MockRestServiceServer.bindTo(builder).build();
        HevyApiController hevyApi = new HevyApiController(builder.build(), delay -> { }, ATTEMPTS);
        return new HevySyncController(hevyApi, syncService, hevyProperties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void expectWorkoutPages() {
        server.expect(requestTo(BASE + "/v1/workouts?page=1&pageSize=10"))
                .andRespond(withSuccess(fixture("workouts-page-1.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/workouts?page=2&pageSize=10"))
                .andRespond(withSuccess(fixture("workouts-page-2.json"), MediaType.APPLICATION_JSON));
    }

    private static String eventsUrl(Instant since) {
        return BASE + "/v1/workouts/events?page=1&pageSize=10&since=" + since;
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("hevy/" + name);
    }

    private UUID userId() {
        return properties.userId();
    }

    private Optional<Instant> lastSyncStart() {
        return jdbc.sql("SELECT last_sync_started_at FROM hevy_sync_state WHERE user_id = ?")
                .param(userId()).query(OffsetDateTime.class).optional().map(OffsetDateTime::toInstant);
    }

    private void recordLastSyncStart(Instant at) {
        jdbc.sql("INSERT INTO hevy_sync_state (user_id, last_sync_started_at) VALUES (?, ?)")
                .params(userId(), at.atOffset(ZoneOffset.UTC)).update();
    }

    private void insertWorkout(String workoutId, String json, Instant hevyUpdatedAt) {
        jdbc.sql("""
                INSERT INTO hevy_workout_raw (user_id, hevy_workout_id, payload, hevy_updated_at)
                VALUES (?, ?, CAST(? AS jsonb), ?)
                """).params(userId(), workoutId, json, hevyUpdatedAt.atOffset(ZoneOffset.UTC)).update();
    }

    private Instant hevyUpdatedAt(String workoutId) {
        return jdbc.sql("SELECT hevy_updated_at FROM hevy_workout_raw WHERE hevy_workout_id = ?")
                .param(workoutId).query(OffsetDateTime.class).single().toInstant();
    }

    private Optional<Instant> deletedAt(String workoutId) {
        return jdbc.sql("SELECT deleted_at FROM hevy_workout_raw WHERE hevy_workout_id = ?")
                .param(workoutId).query((rs, row) -> Optional.ofNullable(rs.getObject(1, OffsetDateTime.class)))
                .single().map(OffsetDateTime::toInstant);
    }
}
