package com.aryehgolob.timesheet.period;

import java.time.LocalDate;

/**
 * Finance's "books are closed through this date" line. Nothing dated on or
 * before it can change, for anyone, until an admin moves the line back.
 *
 * @param lockedThrough last locked day, or null when nothing is locked
 */
public record PeriodLock(LocalDate lockedThrough) {

    public static final PeriodLock NONE = new PeriodLock(null);

    public boolean isLocked(LocalDate date) {
        return lockedThrough != null && !date.isAfter(lockedThrough);
    }
}
