package io.github.nofortunateson.hevyinsights.controller.hevy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Reads every page of a paginated Hevy endpoint, from page 1 until the page count Hevy reports, and
 * returns all items in order. Knows nothing about JSON: each page body is read by the given function,
 * which gets the path and the body.
 * <p>
 * Public only because Java cannot share package-private classes with the parent package: use it only from
 * {@code HevyApiController} ({@code LayerDependencyTests} checks it).
 */
public class HevyPageWalker {

    private final HevyHttpClient httpClient;

    public HevyPageWalker(HevyHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public <T> List<T> fetchAll(String path, Map<String, String> query,
            BiFunction<String, String, Page<T>> pageReader) {
        List<T> items = new ArrayList<>();
        int page = 1;
        int pageCount;
        do {
            Page<T> current = pageReader.apply(path, httpClient.getPage(path, query, page));
            items.addAll(current.items());
            pageCount = current.pageCount();
            page++;
        } while (page <= pageCount);
        return items;
    }

    /** The items of one page and the total number of pages Hevy reports. */
    public record Page<T>(List<T> items, int pageCount) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
