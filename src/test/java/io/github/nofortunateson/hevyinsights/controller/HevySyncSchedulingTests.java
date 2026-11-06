package io.github.nofortunateson.hevyinsights.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nofortunateson.hevyinsights.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * With scheduling on (as in the real app), the sync is registered with the configured delays. The first run
 * is pushed one hour away, so no test ever reaches Hevy.
 */
@SpringBootTest(properties = {
        "hevy-insights.sync.scheduled=true",
        "hevy-insights.sync.initial-delay=PT1H"})
@Import(TestcontainersConfiguration.class)
class HevySyncSchedulingTests {

    @Autowired
    private ScheduledTaskHolder scheduledTasks;

    @Test
    void syncRunsAfterTheInitialDelayAndThenAtTheConfiguredInterval() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(task -> task.toString().contains("HevySyncController.synchronize"))
                .singleElement()
                .isInstanceOfSatisfying(FixedDelayTask.class, task -> {
                    assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofHours(1));
                    assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofHours(6));
                });
    }
}
