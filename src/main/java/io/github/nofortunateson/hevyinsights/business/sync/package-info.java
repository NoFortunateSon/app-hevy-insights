/**
 * Business layer: incremental synchronisation, a first full load and then only changes.
 * Reads from {@code data.hevy} and writes the original JSON and normalised rows through {@code data.db}.
 */
package io.github.nofortunateson.hevyinsights.business.sync;
