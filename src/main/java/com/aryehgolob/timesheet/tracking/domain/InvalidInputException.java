package com.aryehgolob.timesheet.tracking.domain;

/** The message is written for the end user and shown next to the cell. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
