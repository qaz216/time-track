package com.aryehgolob.timesheet.catalog;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only lookups other modules need from the catalog. */
@Component
public class Catalog {

    private final JdbcTemplate jdbc;

    Catalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TaskOption> activeTasks() {
        return jdbc.query("""
                select t.id, p.code, p.name as project_name, t.name as task_name, t.billable
                from task t join project p on p.id = t.project_id
                where t.active and p.active
                order by p.code, t.name
                """,
                (rs, i) -> new TaskOption(rs.getLong("id"), rs.getString("code"), rs.getString("project_name"),
                        rs.getString("task_name"), rs.getBoolean("billable")));
    }

    /** Archived tasks keep their history but take no new time. */
    public boolean isOpenForTime(long taskId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from task t join project p on p.id = t.project_id
                               where t.id = ? and t.active and p.active)
                """, Boolean.class, taskId));
    }
}
