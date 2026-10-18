package com.aryehgolob.timesheet.tracking;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** running_timer rows. The primary key on user_id is what makes "one timer per person" true. */
@Component
class RunningTimers {

    private final JdbcTemplate jdbc;

    RunningTimers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<RunningTimer> find(long userId) {
        return jdbc.query("""
                select r.user_id, r.task_id, p.code || ' · ' || t.name, r.started_at, r.note
                from running_timer r join task t on t.id = r.task_id join project p on p.id = t.project_id
                where r.user_id = ?
                """,
                rs -> rs.next()
                        ? Optional.of(new RunningTimer(rs.getLong(1), rs.getLong(2), rs.getString(3),
                                rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getString(5)))
                        : Optional.empty(),
                userId);
    }

    void start(long userId, long taskId, Instant startedAt, String note) {
        jdbc.update("insert into running_timer (user_id, task_id, started_at, note) values (?, ?, ?, ?)",
                userId, taskId, startedAt.atOffset(ZoneOffset.UTC), note);
    }

    void remove(long userId) {
        jdbc.update("delete from running_timer where user_id = ?", userId);
    }
}
