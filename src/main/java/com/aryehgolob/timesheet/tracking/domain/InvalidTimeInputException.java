package com.aryehgolob.timesheet.tracking.domain;

/** The message is written for the end user and shown next to the cell. */
public class InvalidTimeInputException extends RuntimeException {

    public InvalidTimeInputException(String message) {
        super(message);
    }
}
