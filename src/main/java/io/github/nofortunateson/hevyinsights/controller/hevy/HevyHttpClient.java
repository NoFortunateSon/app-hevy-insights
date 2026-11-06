package io.github.nofortunateson.hevyinsights.controller.hevy;

import static io.github.nofortunateson.hevyinsights.utils.Constants.HEVY_PAGE_SIZE;

import io.github.nofortunateson.hevyinsights.exception.HevyApiException;
import io.github.nofortunateson.hevyinsights.exception.HevyRateLimitExceededException;
import io.github.nofortunateson.hevyinsights.utils.RateLimitRetryPolicy;
import io.github.nofortunateson.hevyinsights.utils.RetrySleeper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Sends one GET for one page to Hevy and returns the body of a successful answer. While Hevy answers 429 it
 * waits and retries as {@link RateLimitRetryPolicy} says; any other non-2xx status is an error.
 * <p>
 * Public only because Java cannot share package-private classes with the parent package: use it only from
 * {@code HevyApiController} ({@code LayerDependencyTests} checks it).
 */
public class HevyHttpClient {

    private final RestClient restClient;
    private final RetrySleeper sleeper;
    private final RateLimitRetryPolicy retryPolicy;

    public HevyHttpClient(RestClient restClient, RetrySleeper sleeper, RateLimitRetryPolicy retryPolicy) {
        this.restClient = restClient;
        this.sleeper = sleeper;
        this.retryPolicy = retryPolicy;
    }

    /** Body of the given page, as text. */
    public String getPage(String path, Map<String, String> query, int page) {
        for (int attempt = 1; ; attempt++) {
            Response response = send(path, query, page);
            if (!response.status().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                return successfulBody(path, response);
            }
            if (!retryPolicy.canRetryAfter(attempt)) {
                throw new HevyRateLimitExceededException(
                        "Hevy rate limit on " + path + " after " + attempt + " attempts");
            }
            pause(retryPolicy.delayAfter(response.retryAfter(), attempt));
        }
    }

    private Response send(String path, Map<String, String> query, int page) {
        return restClient.get()
                .uri(builder -> {
                    builder.path(path)
                            .queryParam("page", page)
                            .queryParam("pageSize", HEVY_PAGE_SIZE);
                    query.forEach(builder::queryParam);
                    return builder.build();
                })
                .exchange((request, res) -> new Response(
                        res.getStatusCode(),
                        res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static String successfulBody(String path, Response response) {
        if (!response.status().is2xxSuccessful()) {
            throw new HevyApiException("Hevy answered HTTP " + response.status().value() + " on " + path,
                    response.status().value());
        }
        return response.body();
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HevyApiException("Interrupted while waiting for the Hevy rate limit", e);
        }
    }

    private record Response(HttpStatusCode status, String retryAfter, String body) {
    }
}
