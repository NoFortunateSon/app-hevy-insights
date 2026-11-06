package io.github.nofortunateson.hevyinsights.hevyclient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            JsonNode body = get(path, query, page);
            for (JsonNode item : requiredArray(body, itemsField)) {
                items.add(mapper.apply(item));
            }
            pageCount = body.path("page_count").asInt(0);
            page++;
        } while (page <= pageCount);
        return items;
    }

    private JsonNode get(String path, Map<String, String> query, int page) {
        for (int attempt = 1; ; attempt++) {
            Response response = restClient.get()
                    .uri(builder -> {
                        builder.path(path)
                                .queryParam("page", page)
                                .queryParam("pageSize", PAGE_SIZE);
                        query.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .exchange((request, res) -> new Response(
                            res.getStatusCode().value(),
                            res.getHeaders().getFirst("Retry-After"),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

            if (response.status() == 429) {
                if (attempt >= maxRateLimitAttempts) {
                    throw new HevyRateLimitedException("Hevy rate limit on " + path + " after "
                            + attempt + " attempts");
                }
                pause(retryDelay(response.retryAfter(), attempt));
                continue;
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new HevyApiException("Hevy answered HTTP " + response.status() + " on " + path,
                        response.status());
            }
            try {
                return JSON.readTree(response.body());
            } catch (JacksonException e) {
                throw new HevyApiException("Hevy returned unreadable JSON on " + path, e);
            }
        }
    }

    static Duration retryDelay(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds >= 0) {
                    return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
                }
            } catch (NumberFormatException ignored) {
                // HTTP-date form or garbage: fall back to backoff.
            }
        }
        Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << Math.min(attempt - 1, 16));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
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
            case "deleted" -> {
                JsonNode deletedAt = node.path("deleted_at");
                yield new HevyWorkoutEvent.Deleted(requiredText(node, "id"),
                        deletedAt.isString() ? parseInstant(deletedAt.asString()) : null);
            }
            default -> throw new HevyApiException("Unknown Hevy workout event type: " + type, -1);
        };
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new HevyApiException("Hevy response lacks field: " + field, -1);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isString()) {
            throw new HevyApiException("Hevy field is not a string: " + field, -1);
        }
        return value.asString();
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isArray()) {
            throw new HevyApiException("Hevy field is not an array: " + field, -1);
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

    private record Response(int status, String retryAfter, String body) {
    }
}
