package com.aryehgolob.timesheet.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.tracking.domain.Decision.Denial;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

class WeekSubmissionServiceTest extends IntegrationTest {

    static final WorkWeek WEEK = new WorkWeek(LocalDate.of(2026, 10, 12));
    static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

    @Autowired
    WeekSubmissionService weeks;

    @Autowired
    TimesheetService timesheets;

    @AfterEach
    void releaseSharedLock() {
        data.unlock();
    }

    @Test
    void submittingFreezesTheWeekAndReopeningThawsIt() {
        CurrentUser sam = data.user();
        long task = data.task();
        long entry = timesheets.addEntry(sam.asActor(), task, WEDNESDAY, "2:00", null);

        weeks.submit(sam.asActor(), sam.id(), WEEK);

        assertDenied(() -> timesheets.addEntry(sam.asActor(), task, WEDNESDAY, "1:00", null), Denial.WEEK_SUBMITTED);
        assertDenied(() -> timesheets.deleteEntry(sam.asActor(), entry), Denial.WEEK_SUBMITTED);

        weeks.reopen(sam.asActor(), sam.id(), WEEK);

        timesheets.addEntry(sam.asActor(), task, WEDNESDAY, "1:00", null);
    }

    @Test
    void submittingTwiceIsRefused() {
        CurrentUser sam = data.user();
        weeks.submit(sam.asActor(), sam.id(), WEEK);

        assertDenied(() -> weeks.submit(sam.asActor(), sam.id(), WEEK), Denial.ALREADY_SUBMITTED);
    }

    @Test
    void nobodyCanSubmitSomeoneElsesWeekNotEvenAnAdmin() {
        CurrentUser sam = data.user();

        assertDenied(() -> weeks.submit(data.user().asActor(), sam.id(), WEEK), Denial.NOT_OWNER);
        assertDenied(() -> weeks.submit(data.admin().asActor(), sam.id(), WEEK), Denial.NOT_OWNER);
    }

    @Test
    void anAdminCanSendAWeekBackButAnotherUserCannot() {
        CurrentUser sam = data.user();
        weeks.submit(sam.asActor(), sam.id(), WEEK);

        assertDenied(() -> weeks.reopen(data.user().asActor(), sam.id(), WEEK), Denial.NOT_OWNER);
        weeks.reopen(data.admin().asActor(), sam.id(), WEEK);
    }

    @Test
    void aFullyLockedWeekCanBeNeitherSubmittedNorReopened() {
        CurrentUser sam = data.user();
        CurrentUser admin = data.admin();
        weeks.submit(sam.asActor(), sam.id(), WEEK);
        data.lockThrough(WEEK.end(), admin.id());

        assertDenied(() -> weeks.reopen(admin.asActor(), sam.id(), WEEK), Denial.PERIOD_LOCKED);
        assertDenied(() -> weeks.submit(sam.asActor(), sam.id(), WEEK.previous()),
                Denial.PERIOD_LOCKED);
    }

    @Test
    void submitAndReopenAreAuditedWithTheTotalAtThatMoment() {
        CurrentUser sam = data.user();
        CurrentUser admin = data.admin();
        timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "7:30", null);

        weeks.submit(sam.asActor(), sam.id(), WEEK);
        weeks.reopen(admin.asActor(), sam.id(), WEEK);

        List<Map<String, Object>> trail = data.audit("week", sam.id() + ":2026-10-12");
        assertThat(trail).extracting(r -> r.get("action")).containsExactly("SUBMIT", "REOPEN");
        assertThat(trail).extracting(r -> r.get("actor_id")).containsExactly(sam.id(), admin.id());
        assertThat((String) trail.get(0).get("after")).contains("\"minutes\": 450");
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Denial denial) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(NotAllowedException.class, e -> assertThat(e.denial()).isEqualTo(denial));
    }
}
