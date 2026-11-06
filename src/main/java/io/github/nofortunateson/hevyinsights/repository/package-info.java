/**
 * Repository layer: data access. The Postgres tables (schema in {@code db/migration}) and the Hevy public API
 * as a read-only data source.
 * <p>
 * {@link io.github.nofortunateson.hevyinsights.repository.HevyWorkoutRepository} is the only class that reads Hevy's JSON,
 * because Hevy may change or drop the API. Never calls create or update endpoints on Hevy.
 */
package io.github.nofortunateson.hevyinsights.repository;
