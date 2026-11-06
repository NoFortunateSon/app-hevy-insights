/**
 * Calls to the Hevy public API: pagination and HTTP 429 handling. No domain logic.
 * <p>
 * The only module that knows the shape of the Hevy API, because Hevy may change or drop it.
 * Read-only: never calls create or update endpoints. Depends on no other module.
 */
package io.github.nofortunateson.hevyinsights.hevyclient;
