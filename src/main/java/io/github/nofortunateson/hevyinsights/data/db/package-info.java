/**
 * Data layer, Postgres: access to the app's own tables (schema in {@code db/migration}).
 * <p>
 * Knows SQL and table shapes; no domain logic. Must not depend on {@code data.hevy}: Hevy's
 * format reaches the database only as the raw JSON handed over by the business layer.
 */
package io.github.nofortunateson.hevyinsights.data.db;
