package com.aryehgolob.timesheet.tracking.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Stream;

/** A Monday-to-Sunday week, identified by its Monday. */
public record WorkWeek(LocalDate start) {

    public WorkWeek {
        if (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A week starts on Monday, got " + start);
        }
    }

    public static WorkWeek containing(LocalDate date) {
        return new WorkWeek(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    public LocalDate end() {
        return start.plusDays(6);
    }

    public List<LocalDate> days() {
        return Stream.iterate(start, d -> d.plusDays(1)).limit(7).toList();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end());
    }

    public WorkWeek previous() {
        return new WorkWeek(start.minusWeeks(1));
    }

    public WorkWeek next() {
        return new WorkWeek(start.plusWeeks(1));
    }
}
