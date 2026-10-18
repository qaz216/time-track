package com.aryehgolob.timesheet.period;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PeriodLocks {

    private final JdbcTemplate jdbc;

    PeriodLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PeriodLock current() {
        return jdbc.query("select locked_through from period_lock where id = 1",
                rs -> rs.next() ? new PeriodLock(rs.getObject(1, LocalDate.class)) : PeriodLock.NONE);
    }
}
