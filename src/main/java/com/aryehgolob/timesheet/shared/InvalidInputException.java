package com.aryehgolob.timesheet.shared;

/** The message is written for the end user and shown next to the cell. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
