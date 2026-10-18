package com.aryehgolob.timesheet.tracking.domain;

public class NotAllowedException extends RuntimeException {

    private final Decision.Denial denial;

    public NotAllowedException(Decision.Denial denial) {
        super(denial.message());
        this.denial = denial;
    }

    public Decision.Denial denial() {
        return denial;
    }
}
