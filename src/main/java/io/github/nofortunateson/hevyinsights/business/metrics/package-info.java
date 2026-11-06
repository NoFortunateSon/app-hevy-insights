/**
 * Business layer: metrics computed from stored data only (loads, volume, frequency, recovery, body measurements).
 * Never calls Hevy, so it must not depend on {@code data.hevy}; it reads through {@code data.db}.
 */
package io.github.nofortunateson.hevyinsights.business.metrics;
