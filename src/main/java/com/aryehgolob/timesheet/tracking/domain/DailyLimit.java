package com.aryehgolob.timesheet.tracking.domain;

import com.aryehgolob.timesheet.shared.InvalidInputException;

/** Nobody works more than 24 hours in a day; anything over is a typo. */
public final class DailyLimit {

    private DailyLimit() {
    }

    /**
     * @param otherSeconds time already logged that day, excluding the entry being saved
     * @param newSeconds   the entry being saved
     */
    public static void check(long otherSeconds, int newSeconds) {
        long total = otherSeconds + newSeconds;
        if (total > TimeInput.SECONDS_PER_DAY) {
            throw new InvalidInputException("That makes " + TimeInput.format(total)
                    + " on this day. A day can hold at most 24:00:00.");
        }
    }
}
