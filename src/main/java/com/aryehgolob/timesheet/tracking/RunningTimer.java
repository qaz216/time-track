package com.aryehgolob.timesheet.tracking;

import java.time.Duration;
import java.time.Instant;

/** A timer that is ticking right now. At most one per person. */
public record RunningTimer(long userId, long taskId, String taskLabel, Instant startedAt, String note) {

    /** Past this, it was probably forgotten: ask before logging it all. */
    public static final int LONG_RUNNING_MINUTES = 10 * 60;

    public int elapsedMinutes(Instant now) {
        return (int) Duration.between(startedAt, now).toMinutes();
    }

    public boolean isLongRunning(Instant now) {
        return elapsedMinutes(now) >= LONG_RUNNING_MINUTES;
    }
}
