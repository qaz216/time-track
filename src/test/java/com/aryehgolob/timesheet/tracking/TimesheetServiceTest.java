package com.aryehgolob.timesheet.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.aryehgolob.timesheet.audit.AuditLog;
import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.tracking.domain.Decision.Denial;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;

/** The write path end to end against real Postgres: rules, constraints, audit. */
class TimesheetServiceTest extends IntegrationTest {

    static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Autowired
    TimesheetService timesheets;

    @Autowired
    AuditLog auditLog;

    @AfterEach
    void releaseSharedLock() {
        data.unlock();
    }

    @Nested
    class Adding {

        @Test
        void storesExactSecondsAndTheStartOfARange() {
            Actor sam = data.user().asActor();

            long id = timesheets.addEntry(sam, data.task(), WEDNESDAY, "9:05-10:42", "  Kickoff  ");

            Map<String, Object> row = data.entry(id);
            assertThat(row.get("seconds")).isEqualTo(5820);
            assertThat(row.get("start_time")).isEqualTo(java.sql.Time.valueOf(LocalTime.of(9, 5)));
            assertThat(row.get("note")).isEqualTo("Kickoff");
        }

        @Test
        void blankNoteIsStoredAsNoNote() {
            long id = timesheets.addEntry(data.user().asActor(), data.task(), WEDNESDAY, "1:00", "   ");

            assertThat(data.entry(id).get("note")).isNull();
        }

        @Test
        void refusesTimeOnAnArchivedTask() {
            long task = data.task();
            data.archiveTask(task);

            assertThatThrownBy(() -> timesheets.addEntry(data.user().asActor(), task, WEDNESDAY, "1:00", null))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        void refusesADayOver24HoursAcrossEntries() {
            Actor sam = data.user().asActor();
            long task = data.task();
            timesheets.addEntry(sam, task, WEDNESDAY, "20:00", null);

            assertThatThrownBy(() -> timesheets.addEntry(sam, task, WEDNESDAY, "4:01", null))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("24:01");
            timesheets.addEntry(sam, task, WEDNESDAY, "4:00", null); // exactly 24:00 is fine
        }

        @Test
        void dailyLimitIsPerPerson() {
            long task = data.task();
            timesheets.addEntry(data.user().asActor(), task, WEDNESDAY, "20:00", null);

            timesheets.addEntry(data.user().asActor(), task, WEDNESDAY, "20:00", null);
        }

        @Test
        void refusesWhenTheWeekIsSubmitted() {
            CurrentUser sam = data.user();
            data.submit(sam.id(), MONDAY);

            assertDenied(() -> timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", null),
                    Denial.WEEK_SUBMITTED);
        }

        @Test
        void refusesInALockedPeriodEvenForAnAdmin() {
            CurrentUser admin = data.admin();
            data.lockThrough(WEDNESDAY, admin.id());

            assertDenied(() -> timesheets.addEntry(admin.asActor(), data.task(), WEDNESDAY, "1:00", null),
                    Denial.PERIOD_LOCKED);
            timesheets.addEntry(admin.asActor(), data.task(), WEDNESDAY.plusDays(1), "1:00", null);
        }
    }

    @Nested
    class ChangingSomeoneElsesEntry {

        @Test
        void isRefusedForAnotherUserAndNothingChanges() {
            Actor sam = data.user().asActor();
            Actor jo = data.user().asActor();
            long samsEntry = timesheets.addEntry(sam, data.task(), WEDNESDAY, "1:00", null);

            assertDenied(() -> timesheets.updateEntry(jo, samsEntry, 0, "8:00", null), Denial.NOT_OWNER);
            assertDenied(() -> timesheets.deleteEntry(jo, samsEntry), Denial.NOT_OWNER);
            assertThat(data.entry(samsEntry).get("seconds")).isEqualTo(3600);
        }

        @Test
        void isRefusedForAnAdminToo() {
            Actor sam = data.user().asActor();
            long samsEntry = timesheets.addEntry(sam, data.task(), WEDNESDAY, "1:00", null);

            assertDenied(() -> timesheets.deleteEntry(data.admin().asActor(), samsEntry), Denial.NOT_OWNER);
        }
    }

    @Nested
    class Editing {

        @Test
        void refusesAnEditMadeOnAnOutdatedVersion() {
            Actor sam = data.user().asActor();
            long id = timesheets.addEntry(sam, data.task(), WEDNESDAY, "1:00", null);
            timesheets.updateEntry(sam, id, 0, "2:00", null); // e.g. from another tab

            assertThatThrownBy(() -> timesheets.updateEntry(sam, id, 0, "3:00", null))
                    .isInstanceOf(StaleEntryException.class);
            assertThat(data.entry(id).get("seconds")).isEqualTo(7200);
        }

        @Test
        void dailyLimitDoesNotCountTheEntryBeingEdited() {
            Actor sam = data.user().asActor();
            long id = timesheets.addEntry(sam, data.task(), WEDNESDAY, "20:00", null);

            timesheets.updateEntry(sam, id, 0, "24:00", null);

            assertThat(data.entry(id).get("seconds")).isEqualTo(86400);
        }

        @Test
        void unknownEntryIsNotFound() {
            assertThatThrownBy(() -> timesheets.deleteEntry(data.user().asActor(), Long.MAX_VALUE))
                    .isInstanceOf(EntryNotFoundException.class);
        }
    }

    @Nested
    class Audit {

        @Test
        void recordsCreateUpdateAndDeleteWithSnapshots() {
            CurrentUser sam = data.user();
            long id = timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", "draft");
            timesheets.updateEntry(sam.asActor(), id, 0, "9:00-10:30", "final");
            timesheets.deleteEntry(sam.asActor(), id);

            List<Map<String, Object>> trail = data.audit("time_entry", id);
            assertThat(trail).extracting(r -> r.get("action")).containsExactly("CREATE", "UPDATE", "DELETE");
            assertThat(trail).extracting(r -> r.get("actor_id")).containsOnly(sam.id());

            assertThat(trail.get(0).get("before")).isNull();
            assertThat((String) trail.get(1).get("before")).contains("\"seconds\": 3600", "\"note\": \"draft\"");
            assertThat((String) trail.get(1).get("after"))
                    .contains("\"seconds\": 5400", "\"startTime\": \"09:00", "\"note\": \"final\"");
            assertThat((String) trail.get(2).get("before")).contains("\"seconds\": 5400");
            assertThat(trail.get(2).get("after")).isNull();
            assertThat(data.entryCount(sam.id())).isZero();
        }

        @Test
        void anEditThatChangesNothingLeavesNoTrailAndKeepsTheVersion() {
            Actor sam = data.user().asActor();
            long id = timesheets.addEntry(sam, data.task(), WEDNESDAY, "1:30", "same");

            timesheets.updateEntry(sam, id, 0, "90m", "same");

            assertThat(data.audit("time_entry", id)).hasSize(1);
            assertThat(data.entry(id).get("version")).isEqualTo(0);
        }

        @Test
        void auditCannotBeWrittenOutsideTheChangesTransaction() {
            assertThatThrownBy(() -> auditLog.record(data.user().asActor(), "time_entry", 1, "UPDATE", null, null))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        void aRefusedChangeLeavesNoTrail() {
            Actor sam = data.user().asActor();
            long id = timesheets.addEntry(sam, data.task(), WEDNESDAY, "1:00", null);

            assertThatThrownBy(() -> timesheets.updateEntry(sam, id, 0, "1.00001", null))
                    .isInstanceOf(InvalidInputException.class);

            assertThat(data.audit("time_entry", id)).hasSize(1);
        }
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Denial denial) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(NotAllowedException.class, e -> assertThat(e.denial()).isEqualTo(denial));
    }
}
