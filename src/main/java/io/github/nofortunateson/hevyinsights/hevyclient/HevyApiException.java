package io.github.nofortunateson.hevyinsights.hevyclient;

/** Hevy answered with an unexpected status or a body this client cannot read. */
public class HevyApiException extends RuntimeException {

    private final int statusCode;

    HevyApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    HevyApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status returned by Hevy, or -1 when the failure was not an HTTP status. */
    public int statusCode() {
        return statusCode;
    }
}
