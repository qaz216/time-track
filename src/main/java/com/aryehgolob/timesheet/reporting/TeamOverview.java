package com.aryehgolob.timesheet.reporting;

import java.util.List;

import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/** Everyone × every week in the period: how much time, and is it submitted. */
public record TeamOverview(ReportPeriod period, List<Row> rows) {

    public record Row(long userId, String name, String email, boolean active, List<Cell> cells, int total) {
    }

    public record Cell(WorkWeek week, int seconds, boolean submitted) {
    }

    public List<WorkWeek> weeks() {
        return period.weeks();
    }

    public long submittedCount(WorkWeek week) {
        return rows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> cell.week().equals(week) && cell.submitted())
                .count();
    }

    public int weekTotal(WorkWeek week) {
        return rows.stream()
                .flatMap(row -> row.cells().stream())
                .filter(cell -> cell.week().equals(week))
                .mapToInt(Cell::seconds)
                .sum();
    }
}
