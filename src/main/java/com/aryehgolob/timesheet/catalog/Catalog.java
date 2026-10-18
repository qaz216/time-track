package com.aryehgolob.timesheet.catalog;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only lookups other modules need from the catalog. */
@Component
public class Catalog {

    /**
     * How an entry's task is named everywhere it's shown, as SQL over aliases
     * {@code p} (project) and {@code t} (task): "Project Administration" for a
     * project's default task, "Website › Design" for a named one. One definition,
     * so the grid, the timer and the picker can't drift apart.
     */
    public static final String LABEL_SQL = "(p.name || case when t.is_default then '' else ' › ' || t.name end)";

    private final JdbcTemplate jdbc;

    Catalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ordered for the picker: by client, then project, default task first. */
    public List<TaskOption> activeTasks() {
        return jdbc.query("""
                select t.id, p.id as project_id, p.client, p.code, p.name as project_name,
                       t.name as task_name, t.is_default, t.billable
                from task t join project p on p.id = t.project_id
                where t.active and p.active
                order by p.client, p.name, t.is_default desc, t.name
                """,
                (rs, i) -> new TaskOption(rs.getLong("id"), rs.getLong("project_id"), rs.getString("client"),
                        rs.getString("code"), rs.getString("project_name"), rs.getString("task_name"),
                        rs.getBoolean("is_default"), rs.getBoolean("billable")));
    }

    /** Archived tasks keep their history but take no new time. */
    public boolean isOpenForTime(long taskId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from task t join project p on p.id = t.project_id
                               where t.id = ? and t.active and p.active)
                """, Boolean.class, taskId));
    }
}
