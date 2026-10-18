package com.aryehgolob.timesheet.tracking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.period.PeriodLock;
import com.aryehgolob.timesheet.period.PeriodLocks;
import com.aryehgolob.timesheet.tracking.WeekView.EntryLine;
import com.aryehgolob.timesheet.tracking.WeekView.TaskRow;
import com.aryehgolob.timesheet.tracking.domain.Decision.Denial;
import com.aryehgolob.timesheet.tracking.domain.EditPolicy;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/** Read side of the week page: one SQL join, no entities. */
@Component
public class WeekQueries {

    private final JdbcTemplate jdbc;
    private final WeekSubmissions submissions;
    private final PeriodLocks locks;

    WeekQueries(JdbcTemplate jdbc, WeekSubmissions submissions, PeriodLocks locks) {
        this.jdbc = jdbc;
        this.submissions = submissions;
        this.locks = locks;
    }

    /** Owners see their own week; admins can look at anyone's. */
    @Transactional(readOnly = true)
    public WeekView week(Actor actor, long ownerId, WorkWeek week) {
        if (!actor.is(ownerId) && !actor.admin()) {
            throw new NotAllowedException(Denial.NOT_OWNER);
        }
        String ownerName = jdbc.queryForObject("select display_name from app_user where id = ?", String.class, ownerId);
        List<EntryLine> lines = jdbc.query("""
                select e.id, p.code, t.name, e.work_date, e.minutes, e.start_time, e.note, e.version
                from time_entry e
                join task t on t.id = e.task_id
                join project p on p.id = t.project_id
                where e.user_id = ? and e.work_date between ? and ?
                order by e.work_date, e.start_time nulls last, e.id
                """,
                (rs, i) -> new EntryLine(rs.getLong(1), rs.getString(2) + " · " + rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getInt(5), rs.getObject(6, LocalTime.class),
                        rs.getString(7), rs.getInt(8)),
                ownerId, week.start(), week.end());
        var submittedAt = submissions.submittedAt(ownerId, week).orElse(null);
        PeriodLock lock = locks.current();
        Set<LocalDate> editable = week.days().stream()
                .filter(day -> EditPolicy.canChangeEntry(actor, ownerId, day, submittedAt != null, lock).isAllowed())
                .collect(Collectors.toUnmodifiableSet());

        boolean submitted = submittedAt != null;
        boolean canSubmit = EditPolicy.canSubmit(actor, ownerId, week, submitted, lock).isAllowed();
        boolean canReopen = EditPolicy.canReopen(actor, ownerId, week, submitted, lock).isAllowed();

        return new WeekView(ownerId, ownerName, week, rows(lines, week), dayTotals(lines, week),
                lines.stream().mapToInt(EntryLine::minutes).sum(), byDay(lines, week), submittedAt, lock, editable,
                canSubmit, canReopen);
    }

    private static List<TaskRow> rows(List<EntryLine> lines, WorkWeek week) {
        Map<String, int[]> perTask = new LinkedHashMap<>();
        lines.stream()
                .sorted((a, b) -> a.taskLabel().compareToIgnoreCase(b.taskLabel()))
                .forEach(line -> perTask.computeIfAbsent(line.taskLabel(), k -> new int[7])
                        [dayIndex(week, line.date())] += line.minutes());
        List<TaskRow> rows = new ArrayList<>();
        perTask.forEach((label, days) -> rows.add(new TaskRow(label, Arrays.stream(days).boxed().toList(), Arrays.stream(days).sum())));
        return rows;
    }

    private static List<Integer> dayTotals(List<EntryLine> lines, WorkWeek week) {
        int[] totals = new int[7];
        lines.forEach(line -> totals[dayIndex(week, line.date())] += line.minutes());
        return Arrays.stream(totals).boxed().toList();
    }

    private static Map<LocalDate, List<EntryLine>> byDay(List<EntryLine> lines, WorkWeek week) {
        Map<LocalDate, List<EntryLine>> byDay = new LinkedHashMap<>();
        week.days().forEach(day -> byDay.put(day, new ArrayList<>()));
        lines.forEach(line -> byDay.get(line.date()).add(line));
        return Collections.unmodifiableMap(byDay);
    }

    private static int dayIndex(WorkWeek week, LocalDate date) {
        return (int) (date.toEpochDay() - week.start().toEpochDay());
    }
}
