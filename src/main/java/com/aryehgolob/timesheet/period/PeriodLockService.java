package com.aryehgolob.timesheet.period;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.audit.AuditLog;
import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.shared.InvalidInputException;

/**
 * Finance closes the books by moving one line forward. Moving it back (or
 * removing it) is allowed, because mistakes happen, but it is admin-only and audited.
 */
@Service
@Transactional
public class PeriodLockService {

    static final String AUDIT_TYPE = "period_lock";

    private final JdbcTemplate jdbc;
    private final PeriodLocks locks;
    private final AuditLog audit;
    private final Clock clock;

    PeriodLockService(JdbcTemplate jdbc, PeriodLocks locks, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.locks = locks;
        this.audit = audit;
        this.clock = clock;
    }

    public void lockThrough(Actor actor, LocalDate lastClosedDay) {
        requireAdmin(actor);
        if (lastClosedDay.isAfter(LocalDate.now(clock))) {
            throw new InvalidInputException("You can only close days that have already happened.");
        }
        PeriodLock before = locks.current();
        jdbc.update("""
                insert into period_lock (id, locked_through, locked_by, locked_at) values (1, ?, ?, now())
                on conflict (id) do update
                    set locked_through = excluded.locked_through, locked_by = excluded.locked_by, locked_at = now()
                """, lastClosedDay, actor.userId());
        audit.record(actor, AUDIT_TYPE, 1, "LOCK", before, new PeriodLock(lastClosedDay));
    }

    public void unlock(Actor actor) {
        requireAdmin(actor);
        PeriodLock before = locks.current();
        if (jdbc.update("delete from period_lock") == 1) {
            audit.record(actor, AUDIT_TYPE, 1, "UNLOCK", before, PeriodLock.NONE);
        }
    }

    /** /admin/** is already admin-only; this holds even if a future URL forgets. */
    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new AccessDeniedException("Only admins can close or reopen periods.");
        }
    }
}
