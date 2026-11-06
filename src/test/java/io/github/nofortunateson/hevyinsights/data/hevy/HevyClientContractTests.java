package io.github.nofortunateson.hevyinsights.data.hevy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Contract tests against recorded-shape responses (invented data, schema from the Hevy docs).
 * No real Hevy call.
 */
class HevyClientContractTests {

    private static final String BASE = "https://hevy.test";
    // Not a real key: an all-zero UUID, the format Hevy documents for the header.
    private static final String FAKE_KEY = "00000000-0000-0000-0000-000000000000";

    // From the Hevy spec, not from HevyClient: the contract is what Hevy documents.
    private static final int HEVY_MAX_PAGE_SIZE = 10;
    private static final Instant SINCE = Instant.parse("2026-10-01T00:00:00Z");

    private final List<Duration> sleeps = new ArrayList<>();

    private MockRestServiceServer server;

    private HevyClient client(String key, int maxAttempts) {
        RestClient.Builder builder = HevyClientConfiguration.restClientBuilder(
                new HevyApiProperties(URI.create(BASE), key, maxAttempts));
        server = MockRestServiceServer.bindTo(builder).build();
        return new HevyClient(builder.build(), sleeps::add, maxAttempts);
    }

    private static String workoutsPage(int page) {
        return BASE + "/v1/workouts?page=" + page + "&pageSize=" + HEVY_MAX_PAGE_SIZE;
    }

    private static String eventsFirstPage(Instant since) {
        return BASE + "/v1/workouts/events?page=1&pageSize=" + HEVY_MAX_PAGE_SIZE + "&since=" + since;
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("data/hevy/" + name);
    }

    @Test
    void fetchAllWorkoutsWalksEveryPageWithMaxPageSizeAndApiKey() {
        HevyClient client = client(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HevyClientConfiguration.API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workouts-page-1.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(workoutsPage(2)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HevyClientConfiguration.API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workouts-page-2.json"), MediaType.APPLICATION_JSON));

        List<HevyWorkout> workouts = client.fetchAllWorkouts();

        server.verify();
        assertThat(workouts).extracting(HevyWorkout::id).containsExactly("w-0001", "w-0002");
        assertThat(workouts.getFirst().updatedAt()).isEqualTo(Instant.parse("2026-10-01T18:10:00Z"));
        assertThat(workouts.getFirst().rawJson())
                .contains("\"exercise_template_id\":\"tmpl-bench\"")
                .contains("\"rpe\":8.5")
                .startsWith("{\"id\":\"w-0001\"");
    }

    @Test
    void fetchAllWorkoutsStopsAfterOneRequestWhenThereAreNoWorkouts() {
        HevyClient client = client(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withSuccess(fixture("workouts-empty.json"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchAllWorkouts()).isEmpty();
        server.verify();
    }

    @Test
    void fetchWorkoutEventsSinceMapsUpdatesAndDeletions() {
        HevyClient client = client(FAKE_KEY, 5);
        server.expect(requestTo(eventsFirstPage(SINCE)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HevyClientConfiguration.API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workout-events-page-1.json"), MediaType.APPLICATION_JSON));

        List<HevyWorkoutEvent> events = client.fetchWorkoutEventsSince(SINCE);

        server.verify();
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOfSatisfying(HevyWorkoutEvent.Updated.class, updated -> {
            assertThat(updated.workout().id()).isEqualTo("w-0002");
            assertThat(updated.workout().updatedAt()).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"));
        });
        assertThat(events.get(1)).isEqualTo(
                new HevyWorkoutEvent.Deleted("w-0001", Instant.parse("2026-10-02T12:00:00Z")));
        assertThat(events.get(2)).isEqualTo(new HevyWorkoutEvent.Deleted("w-0000", null));
    }

    @Test
    void rateLimitHonoursRetryAfterSecondsThenSucceeds() {
        HevyClient client = client(FAKE_KEY, 5);
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.set(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withSuccess(fixture("workouts-empty.json"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchAllWorkouts()).isEmpty();
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void rateLimitWithoutRetryAfterBacksOffExponentiallyAndGivesUp() {
        HevyClient client = client(FAKE_KEY, 4);
        server.expect(times(4), requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(client::fetchAllWorkouts)
                .isInstanceOf(HevyRateLimitedException.class)
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.of(429));
        server.verify();
        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void errorStatusFailsWithoutLeakingTheKey() {
        HevyClient client = client(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::fetchAllWorkouts)
                .isInstanceOf(HevyApiException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining(FAKE_KEY)
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.of(401));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        HevyClient client = client("  ", 5);
        server.expect(never(), anything());

        assertThatThrownBy(client::fetchAllWorkouts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hevy API key missing: set HEVY_API_KEY");
        server.verify();
    }

    @Test
    void unknownEventTypeIsRejected() {
        HevyClient client = client(FAKE_KEY, 5);
        server.expect(requestTo(eventsFirstPage(Instant.EPOCH)))
                .andRespond(withSuccess("{\"page\":1,\"page_count\":1,\"events\":[{\"type\":\"merged\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchWorkoutEventsSince(Instant.EPOCH))
                .isInstanceOf(HevyApiException.class)
                .hasMessageContaining("merged")
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.empty());
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(new HevyApiProperties(URI.create(BASE), FAKE_KEY, 5).toString())
                .doesNotContain(FAKE_KEY)
                .contains("key=***");
    }

    @Test
    void retryAfterIsCappedAndMalformedValuesFallBackToBackoff() {
        assertThat(HevyClient.retryDelay("3600", 1)).isEqualTo(HevyClient.MAX_BACKOFF);
        assertThat(HevyClient.retryDelay("Wed, 21 Oct 2026 07:28:00 GMT", 2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(HevyClient.retryDelay(null, 30)).isEqualTo(HevyClient.MAX_BACKOFF);
    }
}
