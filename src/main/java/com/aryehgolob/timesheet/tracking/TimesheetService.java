package com.aryehgolob.timesheet.tracking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.audit.AuditLog;
import com.aryehgolob.timesheet.catalog.Catalog;
import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.period.PeriodLocks;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.domain.DailyLimit;
import com.aryehgolob.timesheet.tracking.domain.EditPolicy;
import com.aryehgolob.timesheet.tracking.domain.TimeInput;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * The only way time entries change. Each use case runs the same steps in one
 * transaction: load, ask {@link EditPolicy}, validate, save, audit.
 */
@Service
@Transactional
public class TimesheetService {

    static final String AUDIT_TYPE = "time_entry";
    private static final int MAX_NOTE = 500;

    private final TimeEntryRepository entries;
    private final WeekSubmissions submissions;
    private final PeriodLocks locks;
    private final Catalog catalog;
    private final AuditLog audit;
    private final Clock clock;

    TimesheetService(TimeEntryRepository entries, WeekSubmissions submissions, PeriodLocks locks, Catalog catalog,
            AuditLog audit, Clock clock) {
        this.entries = entries;
        this.submissions = submissions;
        this.locks = locks;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
    }

    /** Users log time for themselves only, so the owner is always the actor. */
    public long addEntry(Actor actor, long taskId, LocalDate date, String timeText, String noteText) {
        return addEntry(actor, taskId, date, TimeInput.parse(timeText), noteText);
    }

    /** Same rules, for callers that already have a parsed time (the timer). */
    public long addEntry(Actor actor, long taskId, LocalDate date, TimeInput time, String noteText) {
        checkCanChange(actor, actor.userId(), date);
        String note = cleanNote(noteText);
        requireOpenTask(taskId);
        DailyLimit.check((int) entries.minutesOn(actor.userId(), date), time.minutes());

        TimeEntry entry = entries.save(new TimeEntry(actor.userId(), taskId, date, time, note, now()));
        audit.record(actor, AUDIT_TYPE, entry.getId(), "CREATE", null, entry.snapshot());
        return entry.getId();
    }

    /**
     * @param expectedVersion the version the user was looking at; if someone (or
     *                        another tab) changed it since, refuse rather than overwrite
     */
    public void updateEntry(Actor actor, long entryId, int expectedVersion, String timeText, String noteText) {
        TimeEntry entry = load(entryId);
        checkCanChange(actor, entry.getUserId(), entry.getWorkDate());
        if (entry.getVersion() != expectedVersion) {
            throw new StaleEntryException();
        }
        TimeInput time = TimeInput.parse(timeText);
        String note = cleanNote(noteText);
        int otherMinutes = (int) entries.minutesOn(entry.getUserId(), entry.getWorkDate()) - entry.getMinutes();
        DailyLimit.check(otherMinutes, time.minutes());

        TimeEntry.Snapshot before = entry.snapshot();
        if (entry.change(time, note, now())) {
            audit.record(actor, AUDIT_TYPE, entryId, "UPDATE", before, entry.snapshot());
        }
    }

    public void deleteEntry(Actor actor, long entryId) {
        TimeEntry entry = load(entryId);
        checkCanChange(actor, entry.getUserId(), entry.getWorkDate());

        entries.delete(entry);
        audit.record(actor, AUDIT_TYPE, entryId, "DELETE", entry.snapshot(), null);
    }

    void requireOpenTask(long taskId) {
        if (!catalog.isOpenForTime(taskId)) {
            throw new InvalidInputException("That task is archived or doesn't exist.");
        }
    }

    void checkCanChange(Actor actor, long ownerId, LocalDate date) {
        boolean submitted = submissions.isSubmitted(ownerId, WorkWeek.containing(date));
        EditPolicy.canChangeEntry(actor, ownerId, date, submitted, locks.current()).orThrow();
    }

    private TimeEntry load(long entryId) {
        return entries.findById(entryId).orElseThrow(() -> new EntryNotFoundException(entryId));
    }

    private Instant now() {
        return clock.instant();
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.strip();
        if (trimmed.length() > MAX_NOTE) {
            throw new InvalidInputException("Notes can be at most " + MAX_NOTE + " characters.");
        }
        return trimmed;
    }
}
