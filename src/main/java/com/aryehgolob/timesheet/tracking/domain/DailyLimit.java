package com.aryehgolob.timesheet.tracking.domain;

/** Nobody works more than 24 hours in a day; anything over is a typo. */
public final class DailyLimit {

    private DailyLimit() {
    }

    /**
     * @param otherMinutes minutes already logged that day, excluding the entry being saved
     * @param newMinutes   minutes of the entry being saved
     */
    public static void check(int otherMinutes, int newMinutes) {
        int total = otherMinutes + newMinutes;
        if (total > TimeInput.MINUTES_PER_DAY) {
            throw new InvalidTimeInputException("That makes " + TimeInput.format(total)
                    + " on this day. A day can hold at most 24:00.");
        }
    }
}
