package com.aryehgolob.timesheet.tracking.web;

import java.time.LocalDate;

/** What the quick-add form holds, so a rejected entry comes back exactly as typed. */
record EntryForm(Long taskId, LocalDate date, String time, String note) {

    static EntryForm blank(LocalDate date) {
        return new EntryForm(null, date, "", "");
    }
}
