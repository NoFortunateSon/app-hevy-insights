package io.github.nofortunateson.hevyinsights.data.hevy;

import java.time.Duration;

/** Waits between retries. A seam so tests do not really sleep. */
@FunctionalInterface
interface Sleeper {

    Sleeper REAL = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
