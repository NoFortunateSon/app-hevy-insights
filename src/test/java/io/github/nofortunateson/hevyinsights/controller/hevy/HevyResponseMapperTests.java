package io.github.nofortunateson.hevyinsights.controller.hevy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nofortunateson.hevyinsights.controller.hevy.HevyPageWalker.Page;
import io.github.nofortunateson.hevyinsights.exception.HevyApiException;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutDto;
import io.github.nofortunateson.hevyinsights.model.dto.HevyWorkoutEventDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** JSON to DTO, with invented data shaped like the Hevy spec. */
class HevyResponseMapperTests {

    private static final String PATH = "/v1/test";

    private final HevyResponseMapper mapper = new HevyResponseMapper();

    @Test
    void workoutsPageKeepsIdUpdateTimeRawJsonAndPageCount() {
        Page<HevyWorkoutDto> page = mapper.workoutsPage(PATH, """
                {"page": 1, "page_count": 3, "workouts": [
                  {"id": "w-1", "updated_at": "2026-10-01T18:10:00Z", "title": "Push", "exercises": []}
                ]}
                """);

        assertThat(page.pageCount()).isEqualTo(3);
        assertThat(page.items()).singleElement().satisfies(workout -> {
            assertThat(workout.id()).isEqualTo("w-1");
            assertThat(workout.updatedAt()).isEqualTo(Instant.parse("2026-10-01T18:10:00Z"));
            assertThat(workout.rawJson()).contains("\"title\":\"Push\"");
        });
    }

    @Test
    void missingPageCountMeansNoFurtherPages() {
        assertThat(mapper.workoutsPage(PATH, "{\"workouts\": []}").pageCount()).isZero();
    }

    @Test
    void eventsPageMapsUpdatedAndDeletedWithOptionalDeletedAt() {
        Page<HevyWorkoutEventDto> page = mapper.workoutEventsPage(PATH, """
                {"page_count": 1, "events": [
                  {"type": "updated", "workout": {"id": "w-2", "updated_at": "2026-10-04T09:00:00Z"}},
                  {"type": "deleted", "id": "w-1", "deleted_at": "2026-10-02T12:00:00Z"},
                  {"type": "deleted", "id": "w-0"}
                ]}
                """);

        assertThat(page.items()).hasSize(3);
        assertThat(page.items().get(0)).isInstanceOf(HevyWorkoutEventDto.Updated.class);
        assertThat(page.items().get(1))
                .isEqualTo(new HevyWorkoutEventDto.Deleted("w-1", Instant.parse("2026-10-02T12:00:00Z")));
        assertThat(page.items().get(2)).isEqualTo(new HevyWorkoutEventDto.Deleted("w-0", null));
    }

    @Test
    void unreadableJsonNamesThePath() {
        assertThatThrownBy(() -> mapper.workoutsPage(PATH, "not json"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Hevy returned unreadable JSON on " + PATH);
    }

    @Test
    void missingItemsArrayIsRejected() {
        assertThatThrownBy(() -> mapper.workoutsPage(PATH, "{\"page_count\": 1}"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Hevy response lacks field: workouts");
    }

    @Test
    void workoutWithoutIdIsRejected() {
        assertThatThrownBy(() -> mapper.workoutsPage(PATH,
                "{\"workouts\": [{\"updated_at\": \"2026-10-01T18:10:00Z\"}]}"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Hevy response lacks field: id");
    }

    @Test
    void nonTextIdIsRejected() {
        assertThatThrownBy(() -> mapper.workoutsPage(PATH,
                "{\"workouts\": [{\"id\": 7, \"updated_at\": \"2026-10-01T18:10:00Z\"}]}"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Hevy field is not a string: id");
    }

    @Test
    void unreadableTimestampIsRejected() {
        assertThatThrownBy(() -> mapper.workoutsPage(PATH,
                "{\"workouts\": [{\"id\": \"w-1\", \"updated_at\": \"yesterday\"}]}"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Hevy returned an unreadable timestamp: yesterday");
    }

    @Test
    void unknownEventTypeIsRejected() {
        assertThatThrownBy(() -> mapper.workoutEventsPage(PATH, "{\"events\": [{\"type\": \"merged\"}]}"))
                .isInstanceOf(HevyApiException.class)
                .hasMessage("Unknown Hevy workout event type: merged");
    }
}
