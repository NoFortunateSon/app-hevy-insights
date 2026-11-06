package io.github.nofortunateson.hevyinsights.controller;

import io.github.nofortunateson.hevyinsights.config.HevyApiProperties;
import io.github.nofortunateson.hevyinsights.service.HevySyncService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the Hevy sync (ADR 0008, E1 + E2): shortly after startup, then at a fixed interval counted from the
 * end of the previous run. The interval is not aligned to the clock, so calls never fall exactly on xx:00,
 * as Hevy asks.
 * <p>
 * Reads from Hevy through {@link HevyApiController} and hands the data to {@link HevySyncService}, which
 * stores it (ADR 0007).
 */
@Component
public class HevySyncController {

    private static final Logger LOG = LoggerFactory.getLogger(HevySyncController.class);

    private final HevyApiController hevyApi;
    private final HevySyncService syncService;
    private final HevyApiProperties hevyApiProperties;
    private final Clock clock;

    public HevySyncController(HevyApiController hevyApi, HevySyncService syncService,
            HevyApiProperties hevyApiProperties, Clock clock) {
        this.hevyApi = hevyApi;
        this.syncService = syncService;
        this.hevyApiProperties = hevyApiProperties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${hevy-insights.sync.initial-delay}",
            fixedDelayString = "${hevy-insights.sync.interval}")
    public void synchronize() {
        if (!hevyApiProperties.hasKey()) {
            LOG.warn("Hevy sync skipped: HEVY_API_KEY is not set");
            return;
        }
        Instant startedAt = clock.instant();
        Optional<Instant> since = syncService.lastSyncStart();
        if (since.isEmpty()) {
            var allWorkouts = hevyApi.fetchAllWorkouts();
            syncService.storeFullLoad(allWorkouts, startedAt);
            LOG.info("Hevy sync: first full load, {} workouts", allWorkouts.size());
        } else {
            var events = hevyApi.fetchWorkoutEventsSince(since.get());
            syncService.applyEvents(events, startedAt);
            LOG.info("Hevy sync: {} events since {}", events.size(), since.get());
        }
    }
}
