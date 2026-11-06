/**
 * Service layer: business logic. Incremental sync from Hevy (first full load, then only changes) and
 * metrics computed from stored data only. Coordinates repositories; never called by repositories.
 */
package io.github.nofortunateson.hevyinsights.service;
