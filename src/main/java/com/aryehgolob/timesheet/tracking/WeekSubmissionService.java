package com.aryehgolob.timesheet.tracking;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.audit.AuditLog;
import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.period.PeriodLocks;
import com.aryehgolob.timesheet.tracking.domain.Decision.Denial;
import com.aryehgolob.timesheet.tracking.domain.EditPolicy;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * Draft -> submitted -> (reopened) -> submitted. There is no approval step:
 * the control that matters to finance is the period lock.
 */
@Service
@Transactional
public class WeekSubmissionService {

    static final String AUDIT_TYPE = "week";

    /** What the audit log records: whose week, and how much time it held at that moment. */
    record WeekSnapshot(long userId, LocalDate weekStart, long seconds) {
    }

    private final WeekSubmissions submissions;
    private final TimeEntryRepository entries;
    private final PeriodLocks locks;
    private final AuditLog audit;

    WeekSubmissionService(WeekSubmissions submissions, TimeEntryRepository entries, PeriodLocks locks,
            AuditLog audit) {
        this.submissions = submissions;
        this.entries = entries;
        this.locks = locks;
        this.audit = audit;
    }

    public void submit(Actor actor, long ownerId, WorkWeek week) {
        boolean submitted = submissions.isSubmitted(ownerId, week);
        EditPolicy.canSubmit(actor, ownerId, week, submitted, locks.current()).orThrow();
        if (!submissions.add(ownerId, week)) {
            throw new NotAllowedException(Denial.ALREADY_SUBMITTED);
        }
        audit.record(actor, AUDIT_TYPE, key(ownerId, week), "SUBMIT", null, snapshot(ownerId, week));
    }

    /** The owner takes a week back to fix it, or an admin sends it back. */
    public void reopen(Actor actor, long ownerId, WorkWeek week) {
        boolean submitted = submissions.isSubmitted(ownerId, week);
        EditPolicy.canReopen(actor, ownerId, week, submitted, locks.current()).orThrow();
        if (!submissions.remove(ownerId, week)) {
            throw new NotAllowedException(Denial.NOT_SUBMITTED);
        }
        audit.record(actor, AUDIT_TYPE, key(ownerId, week), "REOPEN", snapshot(ownerId, week), null);
    }

    private WeekSnapshot snapshot(long ownerId, WorkWeek week) {
        return new WeekSnapshot(ownerId, week.start(), entries.secondsBetween(ownerId, week.start(), week.end()));
    }

    static String key(long ownerId, WorkWeek week) {
        return ownerId + ":" + week.start();
    }
}
