package com.aryehgolob.timesheet.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aryehgolob.timesheet.period.PeriodLock;
import com.aryehgolob.timesheet.tracking.domain.TimeInput;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * Everything the week page shows, computed once on the server. The grid is a
 * read-only rollup; entries are the source of truth.
 *
 * @param editableDays days the viewer may change, decided by EditPolicy
 * @param canSubmit    whether the viewer may submit this week (EditPolicy again)
 * @param submittedAt  null while the week is a draft
 */
public record WeekView(
        long ownerId,
        String ownerName,
        WorkWeek week,
        List<TaskRow> rows,
        List<Integer> dayTotals,
        int total,
        Map<LocalDate, List<EntryLine>> entriesByDay,
        Instant submittedAt,
        PeriodLock lock,
        Set<LocalDate> editableDays,
        boolean canSubmit,
        boolean canReopen) {

    public record TaskRow(String label, List<Integer> minutesPerDay, int total) {
    }

    public record EntryLine(long id, String taskLabel, LocalDate date, int minutes, LocalTime startTime,
            String note, int version) {

        /** "09:05-10:42" or "1:37", for display and to pre-fill the edit box. */
        public String asTyped() {
            return new TimeInput(minutes, startTime).asTyped();
        }

        public boolean isRange() {
            return startTime != null;
        }
    }

    public List<LocalDate> days() {
        return week.days();
    }

    public boolean submitted() {
        return submittedAt != null;
    }

    public boolean canEdit(LocalDate day) {
        return editableDays.contains(day);
    }

    public boolean canEditAnyDay() {
        return !editableDays.isEmpty();
    }

    public boolean locked(LocalDate day) {
        return lock.isLocked(day);
    }
}
