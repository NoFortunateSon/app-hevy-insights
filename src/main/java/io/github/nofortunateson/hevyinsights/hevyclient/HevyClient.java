package io.github.nofortunateson.hevyinsights.hevyclient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only access to the Hevy public API. Only GET requests, always.
 * <p>
 * Pages are walked internally: callers get complete lists. HTTP 429 is retried, honouring
 * {@code Retry-After} in seconds when present, otherwise with exponential backoff. Hevy does not
 * document its rate limits, so these choices are defensive guesses.
 */
public class HevyClient {

    /** Maximum allowed by Hevy for workouts and workout events. */
    static final int PAGE_SIZE = 10;

    static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final Sleeper sleeper;
    private final int maxRateLimitAttempts;

    HevyClient(RestClient restClient, Sleeper sleeper, int maxRateLimitAttempts) {
        this.restClient = restClient;
        this.sleeper = sleeper;
        this.maxRateLimitAttempts = maxRateLimitAttempts;
    }

    /** Every workout of the user, all pages. */
    public List<HevyWorkout> fetchAllWorkouts() {
        return fetchAllPages("/v1/workouts", Map.of(), "workouts", HevyClient::toWorkout);
    }

    /**
     * Workout updates and deletions since the given time, all pages, in the order Hevy returns
     * them (documented as newest first).
     */
    public List<HevyWorkoutEvent> fetchWorkoutEventsSince(Instant since) {
        return fetchAllPages("/v1/workouts/events", Map.of("since", since.toString()), "events",
                HevyClient::toEvent);
    }

    private <T> List<T> fetchAllPages(String path, Map<String, String> query, String itemsField,
            Function<JsonNode, T> mapper) {
        List<T> items = new ArrayList<>();
        int page = 1;
        int pageCount;
        do {
            JsonNode body = fetchPage(path, query, page);
            for (JsonNode item : requiredArray(body, itemsField)) {
                items.add(mapper.apply(item));
            }
            pageCount = body.path("page_count").asInt(0);
            page++;
        } while (page <= pageCount);
        return items;
    }

    /** One page, waiting and retrying while Hevy answers 429. */
    private JsonNode fetchPage(String path, Map<String, String> query, int page) {
        for (int attempt = 1; ; attempt++) {
            Response response = send(path, query, page);
            if (!response.status().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                return parseSuccessfulBody(path, response);
            }
            if (attempt >= maxRateLimitAttempts) {
                throw new HevyRateLimitedException(
                        "Hevy rate limit on " + path + " after " + attempt + " attempts");
            }
            pause(retryDelay(response.retryAfter(), attempt));
        }
    }

    private Response send(String path, Map<String, String> query, int page) {
        return restClient.get()
                .uri(builder -> {
                    builder.path(path)
                            .queryParam("page", page)
                            .queryParam("pageSize", PAGE_SIZE);
                    query.forEach(builder::queryParam);
                    return builder.build();
                })
                .exchange((request, res) -> new Response(
                        res.getStatusCode(),
                        res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static JsonNode parseSuccessfulBody(String path, Response response) {
        if (!response.status().is2xxSuccessful()) {
            throw new HevyApiException("Hevy answered HTTP " + response.status().value() + " on " + path,
                    response.status().value());
        }
        try {
            return JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new HevyApiException("Hevy returned unreadable JSON on " + path, e);
        }
    }

    /** {@code Retry-After} in seconds when Hevy sends it, otherwise exponential backoff; both capped. */
    static Duration retryDelay(String retryAfter, int attempt) {
        return retryAfterSeconds(retryAfter).orElseGet(() -> exponentialBackoff(attempt));
    }

    /** Only the delta-seconds form; the HTTP-date form or garbage yields empty. */
    private static Optional<Duration> retryAfterSeconds(String retryAfter) {
        if (retryAfter == null) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return seconds < 0 ? Optional.empty() : Optional.of(capped(Duration.ofSeconds(seconds)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Duration exponentialBackoff(int attempt) {
        Duration backoff = INITIAL_BACKOFF;
        for (int doubling = 1; doubling < attempt && backoff.compareTo(MAX_BACKOFF) < 0; doubling++) {
            backoff = backoff.multipliedBy(2);
        }
        return capped(backoff);
    }

    private static Duration capped(Duration delay) {
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HevyApiException("Interrupted while waiting for the Hevy rate limit", e);
        }
    }

    private static HevyWorkout toWorkout(JsonNode node) {
        return new HevyWorkout(requiredText(node, "id"),
                parseInstant(requiredText(node, "updated_at")), node.toString());
    }

    private static HevyWorkoutEvent toEvent(JsonNode node) {
        String type = requiredText(node, "type");
        return switch (type) {
            case "updated" -> new HevyWorkoutEvent.Updated(toWorkout(required(node, "workout")));
            case "deleted" -> toDeletedEvent(node);
            default -> throw new HevyApiException("Unknown Hevy workout event type: " + type);
        };
    }

    /** {@code deleted_at} is optional in the Hevy spec. */
    private static HevyWorkoutEvent.Deleted toDeletedEvent(JsonNode node) {
        JsonNode deletedAt = node.path("deleted_at");
        return new HevyWorkoutEvent.Deleted(requiredText(node, "id"),
                deletedAt.isString() ? parseInstant(deletedAt.asString()) : null);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new HevyApiException("Hevy response lacks field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isString()) {
            throw new HevyApiException("Hevy field is not a string: " + field);
        }
        return value.asString();
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isArray()) {
            throw new HevyApiException("Hevy field is not an array: " + field);
        }
        return value;
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new HevyApiException("Hevy returned an unreadable timestamp: " + text, e);
        }
    }

    private record Response(HttpStatusCode status, String retryAfter, String body) {
    }
}
