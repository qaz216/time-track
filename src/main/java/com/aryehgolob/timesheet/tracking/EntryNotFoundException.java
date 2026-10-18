package com.aryehgolob.timesheet.tracking;

import com.aryehgolob.timesheet.shared.NotFoundException;

public class EntryNotFoundException extends NotFoundException {

    public EntryNotFoundException(long id) {
        super("That entry doesn't exist. It may have been deleted.");
    }
}
