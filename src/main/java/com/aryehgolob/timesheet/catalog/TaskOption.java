package com.aryehgolob.timesheet.catalog;

/** A task as the entry form offers it. */
public record TaskOption(long id, String projectCode, String projectName, String taskName, boolean billable) {

    public String label() {
        return projectCode + " · " + taskName;
    }
}
