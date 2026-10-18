package com.aryehgolob.timesheet.catalog;

/**
 * A task as the project picker offers it. A default task stands for "the
 * project itself" and is shown under the project's name alone.
 */
public record TaskOption(long id, long projectId, String client, String projectCode, String projectName,
        String taskName, boolean isDefault, boolean billable) {

    public String label() {
        return isDefault ? projectName : projectName + " › " + taskName;
    }

    public String colour() {
        return ProjectColour.of(projectId);
    }
}
