package com.aryehgolob.timesheet.tracking;

public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException(long id) {
        super("No time entry " + id);
    }
}
