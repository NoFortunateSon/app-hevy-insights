package io.github.nofortunateson.hevyinsights.data.hevy;

import java.util.OptionalInt;

/** Hevy answered with an unexpected status or a body this client cannot read. */
public class HevyApiException extends RuntimeException {

    private final OptionalInt statusCode;

    /** The response arrived but its content is not what the client expects. */
    HevyApiException(String message) {
        super(message);
        this.statusCode = OptionalInt.empty();
    }

    HevyApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = OptionalInt.empty();
    }

    /** Hevy answered with an HTTP status the client does not accept. */
    HevyApiException(String message, int statusCode) {
        super(message);
        this.statusCode = OptionalInt.of(statusCode);
    }

    /** HTTP status returned by Hevy; empty when the failure was not an HTTP status. */
    public OptionalInt statusCode() {
        return statusCode;
    }
}
