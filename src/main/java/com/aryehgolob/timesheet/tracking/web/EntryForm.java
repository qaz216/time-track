package com.aryehgolob.timesheet.tracking.web;

import java.time.LocalDate;

/** What the entry bar holds, so a rejected entry comes back exactly as typed. */
record EntryForm(Long taskId, LocalDate date, String start, String end, String duration, String note) {

    static EntryForm blank(LocalDate date) {
        return new EntryForm(null, date, "", "", "", "");
    }

    /** After a successful add: keep the project and day, clear the rest. */
    static EntryForm next(Long taskId, LocalDate date) {
        return new EntryForm(taskId, date, "", "", "", "");
    }
}
