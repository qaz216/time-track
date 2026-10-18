package com.aryehgolob.timesheet.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.reporting.TeamOverview.Cell;
import com.aryehgolob.timesheet.reporting.TeamOverview.Row;

/** Admin reporting. Read-only SQL straight off the tables; no entities involved. */
@Component
public class TeamReports {

    static final String[] CSV_COLUMNS = {
            "email", "name", "week_start", "week_status", "client", "project_code", "project", "task",
            "billable", "seconds", "hours"};

    private final JdbcTemplate jdbc;

    TeamReports(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active people, plus anyone who has since left but logged time in the period. */
    @Transactional(readOnly = true)
    public TeamOverview overview(Actor actor, ReportPeriod period) {
        requireAdmin(actor);
        Map<String, Integer> seconds = new HashMap<>();
        jdbc.query("""
                select user_id, date_trunc('week', work_date)::date as week_start, sum(seconds) as seconds
                from time_entry where work_date between ? and ?
                group by user_id, week_start
                """,
                rs -> { seconds.put(key(rs.getLong(1), rs.getObject(2, LocalDate.class)), rs.getInt(3)); },
                period.start(), period.end());
        Set<String> submitted = new HashSet<>(jdbc.query(
                "select user_id, week_start from week_submission where week_start between ? and ?",
                (rs, i) -> key(rs.getLong(1), rs.getObject(2, LocalDate.class)),
                period.start(), period.end()));

        List<Row> rows = jdbc.query("""
                select u.id, u.display_name, u.email, u.active from app_user u
                where u.active or exists (select 1 from time_entry e
                                          where e.user_id = u.id and e.work_date between ? and ?)
                order by u.display_name, u.id
                """,
                (rs, i) -> {
                    long userId = rs.getLong(1);
                    List<Cell> cells = period.weeks().stream()
                            .map(week -> new Cell(week, seconds.getOrDefault(key(userId, week.start()), 0),
                                    submitted.contains(key(userId, week.start()))))
                            .toList();
                    return new Row(userId, rs.getString(2), rs.getString(3), rs.getBoolean(4), cells,
                            cells.stream().mapToInt(Cell::seconds).sum());
                },
                period.start(), period.end());
        return new TeamOverview(period, rows);
    }

    /**
     * One row per person × week × task: a flat shape finance can pivot however
     * they like. {@code seconds} is exact and is the number to reconcile on;
     * {@code hours} is the same value to two decimals, for humans.
     */
    @Transactional(readOnly = true)
    public void writeCsv(Actor actor, ReportPeriod period, Appendable out) {
        requireAdmin(actor);
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(CSV_COLUMNS).get())) {
            jdbc.query("""
                    select u.email, u.display_name, w.week_start, (s.user_id is not null) as submitted,
                           p.client, p.code, p.name as project, t.name as task, t.billable, sum(e.seconds) as seconds
                    from time_entry e
                    cross join lateral (select date_trunc('week', e.work_date)::date as week_start) w
                    join app_user u on u.id = e.user_id
                    join task t on t.id = e.task_id
                    join project p on p.id = t.project_id
                    left join week_submission s on s.user_id = e.user_id and s.week_start = w.week_start
                    where e.work_date between ? and ?
                    group by u.email, u.display_name, w.week_start, s.user_id, p.client, p.code, p.name, t.name, t.billable
                    order by u.display_name, u.email, w.week_start, p.code, t.name
                    """,
                    rs -> {
                        int secs = rs.getInt("seconds");
                        print(csv, safe(rs.getString("email")), safe(rs.getString("display_name")),
                                rs.getObject("week_start", LocalDate.class),
                                rs.getBoolean("submitted") ? "submitted" : "draft",
                                safe(rs.getString("client")), safe(rs.getString("code")),
                                safe(rs.getString("project")), safe(rs.getString("task")),
                                rs.getBoolean("billable") ? "yes" : "no",
                                secs, hours(secs));
                    },
                    period.start(), period.end());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BigDecimal hours(int seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    /**
     * Spreadsheets execute cells that start with = + - @ (CSV/formula injection).
     * Names and project titles are free text, so neutralise them with a leading quote.
     */
    static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static void print(CSVPrinter csv, Object... values) {
        try {
            csv.printRecord(values);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String key(long userId, LocalDate weekStart) {
        return userId + ":" + weekStart;
    }

    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new AccessDeniedException("Only admins can see team reports.");
        }
    }
}
