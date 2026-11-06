/**
 * Data layer, Hevy source: calls to the Hevy public API, pagination and HTTP 429 handling. No domain logic.
 * <p>
 * The only package that knows the shape of the Hevy API, because Hevy may change or drop it.
 * Read-only: never calls create or update endpoints. Depends on no other package of the app.
 */
package io.github.nofortunateson.hevyinsights.data.hevy;
