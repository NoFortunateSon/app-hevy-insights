package io.github.nofortunateson.hevyinsights.controller.hevy;

import io.github.nofortunateson.hevyinsights.controller.hevy.HevyPageWalker.Page;
import io.github.nofortunateson.hevyinsights.exception.HevyApiException;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutDto;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutEventDto;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns Hevy's JSON into our DTOs and checks the fields we rely on. The only class that knows Hevy's JSON
 * field names, because Hevy may change or drop the API.
 * <p>
 * Public only because Java cannot share package-private classes with the parent package: use it only from
 * {@code HevyApiController} ({@code LayerDependencyTests} checks it).
 */
public class HevyResponseMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A page of {@code GET /v1/workouts}; {@code path} only goes into error messages. */
    public Page<HevyWorkoutDto> workoutsPage(String path, String body) {
        return page(path, body, "workouts", HevyResponseMapper::toWorkout);
    }

    /** A page of {@code GET /v1/workouts/events}; {@code path} only goes into error messages. */
    public Page<HevyWorkoutEventDto> workoutEventsPage(String path, String body) {
        return page(path, body, "events", HevyResponseMapper::toEvent);
    }

    private static <T> Page<T> page(String path, String body, String itemsField, Function<JsonNode, T> toDto) {
        JsonNode root = parse(path, body);
        List<T> items = new ArrayList<>();
        for (JsonNode item : requiredArray(root, itemsField)) {
            items.add(toDto.apply(item));
        }
        return new Page<>(items, root.path("page_count").asInt(0));
    }

    private static JsonNode parse(String path, String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            throw new HevyApiException("Hevy returned unreadable JSON on " + path, e);
        }
    }

    private static HevyWorkoutDto toWorkout(JsonNode node) {
        return new HevyWorkoutDto(requiredText(node, "id"),
                parseInstant(requiredText(node, "updated_at")), node.toString());
    }

    private static HevyWorkoutEventDto toEvent(JsonNode node) {
        String type = requiredText(node, "type");
        return switch (type) {
            case "updated" -> new HevyWorkoutEventDto.Updated(toWorkout(required(node, "workout")));
            case "deleted" -> toDeletedEvent(node);
            default -> throw new HevyApiException("Unknown Hevy workout event type: " + type);
        };
    }

    /** {@code deleted_at} is optional in the Hevy spec. */
    private static HevyWorkoutEventDto.Deleted toDeletedEvent(JsonNode node) {
        JsonNode deletedAt = node.path("deleted_at");
        return new HevyWorkoutEventDto.Deleted(requiredText(node, "id"),
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
}
