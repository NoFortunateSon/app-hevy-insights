/**
 * Controller layer: the application's HTTP boundary. Exposes the services (read-only REST over the metrics)
 * and talks to the Hevy public API ({@link io.github.nofortunateson.hevyinsights.controller.HevyApiController},
 * the only class that reads Hevy's JSON). Calls services, never repositories.
 * <p>
 * The sync starts here: {@code HevyApiController} reads from Hevy and hands the data to a service, which
 * stores it through a repository (ADR 0007).
 */
package io.github.nofortunateson.hevyinsights.controller;
