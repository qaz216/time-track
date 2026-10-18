package com.aryehgolob.timesheet.tracking.domain;

import static com.aryehgolob.timesheet.tracking.domain.Decision.Denial.ALREADY_SUBMITTED;
import static com.aryehgolob.timesheet.tracking.domain.Decision.Denial.NOT_OWNER;
import static com.aryehgolob.timesheet.tracking.domain.Decision.Denial.NOT_SUBMITTED;
import static com.aryehgolob.timesheet.tracking.domain.Decision.Denial.PERIOD_LOCKED;
import static com.aryehgolob.timesheet.tracking.domain.Decision.Denial.WEEK_SUBMITTED;

import java.time.LocalDate;

import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.period.PeriodLock;

/**
 * Every "may I change this?" rule in one place. Services ask; they never decide.
 *
 * <ul>
 *   <li>A lock beats everything, admins included.</li>
 *   <li>Only the owner logs or edits their time. Admins don't edit other people's
 *       hours: a timesheet is that person's statement. Admins can reopen a week instead.</li>
 *   <li>A submitted week is read-only until it is reopened.</li>
 * </ul>
 */
public final class EditPolicy {

    private EditPolicy() {
    }

    /** Create, edit or delete an entry on {@code workDate} owned by {@code ownerId}. */
    public static Decision canChangeEntry(Actor actor, long ownerId, LocalDate workDate,
            boolean weekSubmitted, PeriodLock lock) {
        if (lock.isLocked(workDate)) {
            return Decision.denied(PERIOD_LOCKED);
        }
        if (!actor.is(ownerId)) {
            return Decision.denied(NOT_OWNER);
        }
        if (weekSubmitted) {
            return Decision.denied(WEEK_SUBMITTED);
        }
        return Decision.allowed();
    }

    public static Decision canSubmit(Actor actor, long ownerId, WorkWeek week,
            boolean weekSubmitted, PeriodLock lock) {
        if (lock.isLocked(week.end())) { // a week straddling the line stays partly editable
            return Decision.denied(PERIOD_LOCKED);
        }
        if (!actor.is(ownerId)) {
            return Decision.denied(NOT_OWNER);
        }
        if (weekSubmitted) {
            return Decision.denied(ALREADY_SUBMITTED);
        }
        return Decision.allowed();
    }

    /** The owner can take a week back to fix it; an admin can send it back. */
    public static Decision canReopen(Actor actor, long ownerId, WorkWeek week,
            boolean weekSubmitted, PeriodLock lock) {
        if (lock.isLocked(week.end())) { // a week straddling the line stays partly editable
            return Decision.denied(PERIOD_LOCKED);
        }
        if (!actor.is(ownerId) && !actor.admin()) {
            return Decision.denied(NOT_OWNER);
        }
        if (!weekSubmitted) {
            return Decision.denied(NOT_SUBMITTED);
        }
        return Decision.allowed();
    }
}
