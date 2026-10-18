package com.aryehgolob.timesheet.shared;

/** Something the URL points at doesn't exist. The message is shown to the user. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
