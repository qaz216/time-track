package com.aryehgolob.timesheet.tracking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/** A week is a draft unless it has a row in week_submission. */
@Component
class WeekSubmissions {

    private final JdbcTemplate jdbc;

    WeekSubmissions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean isSubmitted(long userId, WorkWeek week) {
        return submittedAt(userId, week).isPresent();
    }

    Optional<Instant> submittedAt(long userId, WorkWeek week) {
        return jdbc.query("select submitted_at from week_submission where user_id = ? and week_start = ?",
                rs -> rs.next() ? Optional.of(rs.getObject(1, OffsetDateTime.class).toInstant()) : Optional.empty(),
                userId, week.start());
    }

    /** @return false if it was already submitted (e.g. a double click that lost the race) */
    boolean add(long userId, WorkWeek week) {
        return jdbc.update("insert into week_submission (user_id, week_start) values (?, ?) on conflict do nothing",
                userId, week.start()) == 1;
    }

    boolean remove(long userId, WorkWeek week) {
        return jdbc.update("delete from week_submission where user_id = ? and week_start = ?",
                userId, week.start()) == 1;
    }
}
