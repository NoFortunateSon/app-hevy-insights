package io.github.nofortunateson.hevyinsights.controller;

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

import io.github.nofortunateson.hevyinsights.config.HevyApiProperties;
import io.github.nofortunateson.hevyinsights.config.HevyApiConfiguration;
import io.github.nofortunateson.hevyinsights.exception.HevyApiException;
import io.github.nofortunateson.hevyinsights.exception.HevyRateLimitExceededException;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutDto;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutEventDto;
import io.github.nofortunateson.hevyinsights.utils.Constants;
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
class HevyApiControllerContractTests {

    private static final String BASE = "https://hevy.test";
    // Not a real key: an all-zero UUID, the format Hevy documents for the header.
    private static final String FAKE_KEY = "00000000-0000-0000-0000-000000000000";

    // From the Hevy spec, not from HevyApiController: the contract is what Hevy documents.
    private static final int HEVY_MAX_PAGE_SIZE = 10;
    private static final Instant SINCE = Instant.parse("2026-10-01T00:00:00Z");

    private final List<Duration> sleeps = new ArrayList<>();

    private MockRestServiceServer server;

    private HevyApiController controller(String key, int maxAttempts) {
        RestClient.Builder builder = HevyApiConfiguration.restClientBuilder(
                new HevyApiProperties(URI.create(BASE), key, maxAttempts));
        server = MockRestServiceServer.bindTo(builder).build();
        return new HevyApiController(builder.build(), sleeps::add, maxAttempts);
    }

    private static String workoutsPage(int page) {
        return BASE + "/v1/workouts?page=" + page + "&pageSize=" + HEVY_MAX_PAGE_SIZE;
    }

    private static String eventsFirstPage(Instant since) {
        return BASE + "/v1/workouts/events?page=1&pageSize=" + HEVY_MAX_PAGE_SIZE + "&since=" + since;
    }

    private static ClassPathResource fixture(String name) {
        return new ClassPathResource("hevy/" + name);
    }

    @Test
    void fetchAllWorkoutsWalksEveryPageWithMaxPageSizeAndApiKey() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(Constants.HEVY_API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workouts-page-1.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(workoutsPage(2)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(Constants.HEVY_API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workouts-page-2.json"), MediaType.APPLICATION_JSON));

        List<HevyWorkoutDto> workouts = controller.fetchAllWorkouts();

        server.verify();
        assertThat(workouts).extracting(HevyWorkoutDto::id).containsExactly("w-0001", "w-0002");
        assertThat(workouts.getFirst().updatedAt()).isEqualTo(Instant.parse("2026-10-01T18:10:00Z"));
        assertThat(workouts.getFirst().rawJson())
                .contains("\"exercise_template_id\":\"tmpl-bench\"")
                .contains("\"rpe\":8.5")
                .startsWith("{\"id\":\"w-0001\"");
    }

    @Test
    void fetchAllWorkoutsStopsAfterOneRequestWhenThereAreNoWorkouts() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withSuccess(fixture("workouts-empty.json"), MediaType.APPLICATION_JSON));

        assertThat(controller.fetchAllWorkouts()).isEmpty();
        server.verify();
    }

    @Test
    void fetchWorkoutEventsSinceMapsUpdatesAndDeletions() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        server.expect(requestTo(eventsFirstPage(SINCE)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(Constants.HEVY_API_KEY_HEADER, FAKE_KEY))
                .andRespond(withSuccess(fixture("workout-events-page-1.json"), MediaType.APPLICATION_JSON));

        List<HevyWorkoutEventDto> events = controller.fetchWorkoutEventsSince(SINCE);

        server.verify();
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOfSatisfying(HevyWorkoutEventDto.Updated.class, updated -> {
            assertThat(updated.workout().id()).isEqualTo("w-0002");
            assertThat(updated.workout().updatedAt()).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"));
        });
        assertThat(events.get(1)).isEqualTo(
                new HevyWorkoutEventDto.Deleted("w-0001", Instant.parse("2026-10-02T12:00:00Z")));
        assertThat(events.get(2)).isEqualTo(new HevyWorkoutEventDto.Deleted("w-0000", null));
    }

    @Test
    void rateLimitHonoursRetryAfterSecondsThenSucceeds() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.set(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withSuccess(fixture("workouts-empty.json"), MediaType.APPLICATION_JSON));

        assertThat(controller.fetchAllWorkouts()).isEmpty();
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void rateLimitWithoutRetryAfterBacksOffExponentiallyAndGivesUp() {
        HevyApiController controller = controller(FAKE_KEY, 4);
        server.expect(times(4), requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(controller::fetchAllWorkouts)
                .isInstanceOf(HevyRateLimitExceededException.class)
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.of(429));
        server.verify();
        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void errorStatusFailsWithoutLeakingTheKey() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        server.expect(requestTo(workoutsPage(1)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(controller::fetchAllWorkouts)
                .isInstanceOf(HevyApiException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining(FAKE_KEY)
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.of(401));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        HevyApiController controller = controller("  ", 5);
        server.expect(never(), anything());

        assertThatThrownBy(controller::fetchAllWorkouts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hevy API key missing: set HEVY_API_KEY");
        server.verify();
    }

    @Test
    void unknownEventTypeIsRejected() {
        HevyApiController controller = controller(FAKE_KEY, 5);
        server.expect(requestTo(eventsFirstPage(Instant.EPOCH)))
                .andRespond(withSuccess("{\"page\":1,\"page_count\":1,\"events\":[{\"type\":\"merged\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> controller.fetchWorkoutEventsSince(Instant.EPOCH))
                .isInstanceOf(HevyApiException.class)
                .hasMessageContaining("merged")
                .extracting(e -> ((HevyApiException) e).statusCode()).isEqualTo(OptionalInt.empty());
    }

    @Test
    void interruptedWaitAfterRateLimitFailsAndKeepsTheInterruptFlag() {
        RestClient.Builder builder = HevyApiConfiguration.restClientBuilder(
                new HevyApiProperties(URI.create(BASE), FAKE_KEY, 5));
        server = MockRestServiceServer.bindTo(builder).build();
        HevyApiController controller = new HevyApiController(builder.build(), delay -> {
            throw new InterruptedException("stop");
        }, 5);
        server.expect(requestTo(workoutsPage(1))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        try {
            assertThatThrownBy(controller::fetchAllWorkouts)
                    .isInstanceOf(HevyApiException.class)
                    .hasMessage("Interrupted while waiting for the Hevy rate limit");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag for the next tests on this thread
        }
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(new HevyApiProperties(URI.create(BASE), FAKE_KEY, 5).toString())
                .doesNotContain(FAKE_KEY)
                .contains("key=***");
    }
}
