package com.aryehgolob.timesheet.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aryehgolob.timesheet.identity.AppUser;
import com.aryehgolob.timesheet.identity.AppUserRepository;
import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.identity.Role;

/**
 * Creates uniquely named fixtures so tests never collide on shared data.
 * The one shared thing is the period lock (a single row): tests that set it
 * must call {@link #unlock()} afterwards.
 */
@TestComponent
public class TestData {

    public static final String PASSWORD = "correct horse";
    private static final AtomicInteger sequence = new AtomicInteger();

    private final AppUserRepository users;
    private final UserDetailsService userDetails;
    private final JdbcTemplate jdbc;
    private final String passwordHash;

    TestData(AppUserRepository users, UserDetailsService userDetails, JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.users = users;
        this.userDetails = userDetails;
        this.jdbc = jdbc;
        this.passwordHash = encoder.encode(PASSWORD);
    }

    public CurrentUser user() {
        return create(Role.USER);
    }

    public CurrentUser admin() {
        return create(Role.ADMIN);
    }

    public CurrentUser inactiveUser() {
        AppUser user = new AppUser(uniqueEmail(), "Former Employee", passwordHash, Role.USER);
        user.deactivate();
        users.save(user);
        return (CurrentUser) userDetails.loadUserByUsername(user.getEmail());
    }

    /** A fresh active project with one task; returns the task id. */
    public long task() {
        int n = sequence.incrementAndGet();
        return task("Project " + n, "Client " + n, "Task " + n);
    }

    public long task(String projectName, String client, String taskName) {
        int n = sequence.incrementAndGet();
        Long projectId = jdbc.queryForObject(
                "insert into project (code, name, client) values (?, ?, ?) returning id",
                Long.class, "P" + n + "-" + (System.nanoTime() % 100000), projectName, client);
        return jdbc.queryForObject("insert into task (project_id, name) values (?, ?) returning id",
                Long.class, projectId, taskName);
    }

    /** A project you log against directly (its default task); returns the task id. */
    public long projectTask(String projectName) {
        int n = sequence.incrementAndGet();
        Long projectId = jdbc.queryForObject(
                "insert into project (code, name, client) values (?, ?, ?) returning id",
                Long.class, "D" + n + "-" + (System.nanoTime() % 100000), projectName, "Client " + n);
        return jdbc.queryForObject(
                "insert into task (project_id, name, is_default) values (?, 'General', true) returning id",
                Long.class, projectId);
    }

    public CurrentUser userNamed(String displayName) {
        AppUser user = users.save(new AppUser(uniqueEmail(), displayName, passwordHash, Role.USER));
        return (CurrentUser) userDetails.loadUserByUsername(user.getEmail());
    }

    public void deactivate(long userId) {
        jdbc.update("update app_user set active = false where id = ?", userId);
    }

    /** A timer that started at a chosen moment, as if the user pressed Start back then. */
    public void timerStartedAt(long userId, long taskId, java.time.Instant startedAt) {
        jdbc.update("insert into running_timer (user_id, task_id, started_at) values (?, ?, ?)",
                userId, taskId, startedAt.atOffset(java.time.ZoneOffset.UTC));
    }

    public boolean hasTimer(long userId) {
        return jdbc.queryForObject("select exists (select 1 from running_timer where user_id = ?)",
                Boolean.class, userId);
    }

    public void archiveTask(long taskId) {
        jdbc.update("update task set active = false where id = ?", taskId);
    }

    public void submit(long userId, LocalDate monday) {
        jdbc.update("insert into week_submission (user_id, week_start) values (?, ?)", userId, monday);
    }

    public void lockThrough(LocalDate date, long adminId) {
        jdbc.update("""
                insert into period_lock (id, locked_through, locked_by) values (1, ?, ?)
                on conflict (id) do update set locked_through = excluded.locked_through
                """, date, adminId);
    }

    public void unlock() {
        jdbc.update("delete from period_lock");
    }

    public int entryCount(long userId) {
        return jdbc.queryForObject("select count(*) from time_entry where user_id = ?", Integer.class, userId);
    }

    public Map<String, Object> entry(long entryId) {
        return jdbc.queryForMap("select * from time_entry where id = ?", entryId);
    }

    /** Audit rows for one entity, oldest first, with before/after as JSON text. */
    public List<Map<String, Object>> audit(String entityType, Object entityId) {
        return jdbc.queryForList("""
                select action, actor_id, before::text as before, after::text as after
                from audit_event where entity_type = ? and entity_id = ? order by id
                """, entityType, String.valueOf(entityId));
    }

    private CurrentUser create(Role role) {
        AppUser user = users.save(new AppUser(uniqueEmail(), "Test " + role, passwordHash, role));
        return (CurrentUser) userDetails.loadUserByUsername(user.getEmail());
    }

    private static String uniqueEmail() {
        return "user" + sequence.incrementAndGet() + "-" + System.nanoTime() + "@test.example";
    }
}
