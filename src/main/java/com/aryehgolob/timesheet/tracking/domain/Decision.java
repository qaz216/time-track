package com.aryehgolob.timesheet.tracking.domain;

/** The answer to "may this actor do this?" Denials carry a reason the UI can show. */
public record Decision(Denial denial) {

    public enum Denial {
        NOT_OWNER("You can only change your own timesheet."),
        PERIOD_LOCKED("This period is closed by finance."),
        WEEK_SUBMITTED("This week is submitted. Reopen it to make changes."),
        ALREADY_SUBMITTED("This week is already submitted."),
        NOT_SUBMITTED("This week isn't submitted.");

        private final String message;

        Denial(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private static final Decision ALLOWED = new Decision(null);

    public static Decision allowed() {
        return ALLOWED;
    }

    public static Decision denied(Denial denial) {
        return new Decision(denial);
    }

    public boolean isAllowed() {
        return denial == null;
    }

    public void orThrow() {
        if (denial != null) {
            throw new NotAllowedException(denial);
        }
    }
}
