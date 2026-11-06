/**
 * Helpers of {@code HevyApiController}, one task each: HTTP call ({@code HevyHttpClient}), pages
 * ({@code HevyPageWalker}), JSON to DTO ({@code HevyResponseMapper}). Everything that knows the shape of the
 * Hevy API stays here, apart from the fixed values in {@code utils.Constants}.
 * <p>
 * The classes are public only because Java cannot share package-private classes with the parent package.
 * Only {@code controller.HevyApiController} may use them: {@code LayerDependencyTests} enforces it.
 */
package io.github.nofortunateson.hevyinsights.controller.hevy;
