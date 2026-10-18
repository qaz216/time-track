package com.aryehgolob.timesheet.reporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * A run of whole weeks. Reports are always week-aligned because weeks are what
 * people submit; a range that starts mid-week is widened to the Monday.
 */
public record ReportPeriod(WorkWeek first, WorkWeek last) {

    static final int MAX_WEEKS = 53;

    public ReportPeriod {
        if (last.start().isBefore(first.start())) {
            throw new InvalidInputException("The end of the period is before its start.");
        }
        if (ChronoUnit.WEEKS.between(first.start(), last.start()) >= MAX_WEEKS) {
            throw new InvalidInputException("Pick a period of at most a year.");
        }
    }

    public static ReportPeriod between(LocalDate from, LocalDate to) {
        return new ReportPeriod(WorkWeek.containing(from), WorkWeek.containing(to));
    }

    public LocalDate start() {
        return first.start();
    }

    public LocalDate end() {
        return last.end();
    }

    public List<WorkWeek> weeks() {
        return Stream.iterate(first, w -> !w.start().isAfter(last.start()), WorkWeek::next).toList();
    }
}
