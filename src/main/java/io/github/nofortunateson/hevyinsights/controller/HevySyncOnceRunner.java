package io.github.nofortunateson.hevyinsights.controller;

import io.github.nofortunateson.hevyinsights.config.HevyApiProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * "One sync, then exit" mode for the scheduled GitHub Actions run (ADR 0010), on with
 * {@code hevy-insights.sync.run-once=true}. Runs one sync during startup; any failure stops the startup, so
 * the process exits with a non-zero code and the workflow run fails visibly.
 * <p>
 * Unlike the periodic sync, a missing API key is an error here: the run exists only to sync.
 */
@Component
@ConditionalOnProperty(name = "hevy-insights.sync.run-once", havingValue = "true")
public class HevySyncOnceRunner implements ApplicationRunner {

    private final HevySyncController syncController;
    private final HevyApiProperties hevyApiProperties;

    public HevySyncOnceRunner(HevySyncController syncController, HevyApiProperties hevyApiProperties) {
        this.syncController = syncController;
        this.hevyApiProperties = hevyApiProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!hevyApiProperties.hasKey()) {
            throw new IllegalStateException("Hevy API key missing: set HEVY_API_KEY");
        }
        syncController.synchronize();
    }
}
