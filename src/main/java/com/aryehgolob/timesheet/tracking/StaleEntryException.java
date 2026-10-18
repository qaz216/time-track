package com.aryehgolob.timesheet.tracking;

import com.aryehgolob.timesheet.tracking.domain.InvalidInputException;

/** The entry changed after the user opened it (another tab, another device). */
public class StaleEntryException extends InvalidInputException {

    public StaleEntryException() {
        super("This entry changed since you opened it. Here is the latest version.");
    }
}
