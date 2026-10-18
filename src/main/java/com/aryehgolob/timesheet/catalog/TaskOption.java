package com.aryehgolob.timesheet.catalog;

/** A task as the entry form offers it, grouped under its project. */
public record TaskOption(long id, String projectCode, String projectName, String taskName, boolean billable) {
}
