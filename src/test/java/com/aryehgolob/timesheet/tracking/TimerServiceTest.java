package com.aryehgolob.timesheet.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

class TimerServiceTest extends IntegrationTest {

    @Autowired
    TimerService timers;

    @Autowired
    WeekSubmissionService submissions;

    @Autowired
    Clock clock;

    /** A wall-clock moment in the office zone, two weeks ago so it's safely in the past. */
    LocalDateTime twoWeeksAgo(int hour, int minute) {
        return LocalDate.now(clock).minusWeeks(2).atTime(hour, minute);
    }

    Instant instant(LocalDateTime local) {
        return local.atZone(clock.getZone()).toInstant();
    }

    @Test
    void stoppingLogsTheClockRangeAsAnEntry() {
        CurrentUser sam = data.user();
        long task = data.task();
        LocalDateTime start = twoWeeksAgo(9, 5);
        data.timerStartedAt(sam.id(), task, instant(start).plusSeconds(40));

        List<Long> ids = timers.stop(sam.asActor(), start.withHour(10).withMinute(42));

        assertThat(ids).singleElement().satisfies(id -> {
            assertThat(data.entry(id).get("minutes")).isEqualTo(97);
            assertThat(data.entry(id).get("start_time")).isEqualTo(Time.valueOf(LocalTime.of(9, 5)));
        });
        assertThat(data.hasTimer(sam.id())).isFalse();
    }

    @Test
    void aTimerForgottenOvernightBecomesOneEntryPerDay() {
        CurrentUser sam = data.user();
        LocalDateTime start = twoWeeksAgo(22, 30);
        data.timerStartedAt(sam.id(), data.task(), instant(start));

        List<Long> ids = timers.stop(sam.asActor(), start.plusDays(1).withHour(8).withMinute(15));

        assertThat(ids).hasSize(2);
        assertThat(data.entry(ids.get(0)).get("work_date")).isEqualTo(Date.valueOf(start.toLocalDate()));
        assertThat(data.entry(ids.get(0)).get("minutes")).isEqualTo(90);
        assertThat(data.entry(ids.get(1)).get("work_date")).isEqualTo(Date.valueOf(start.toLocalDate().plusDays(1)));
        assertThat(data.entry(ids.get(1)).get("minutes")).isEqualTo(495);
        assertThat(data.audit("time_entry", ids.get(1))).extracting(r -> r.get("action")).containsExactly("CREATE");
    }

    @Test
    void aTimerStoppedWithinAMinuteStillLogsOneMinute() {
        CurrentUser sam = data.user();
        data.timerStartedAt(sam.id(), data.task(), clock.instant().minusSeconds(20));

        List<Long> ids = timers.stop(sam.asActor(), null);

        assertThat(ids).singleElement().satisfies(id -> assertThat(data.entry(id).get("minutes")).isEqualTo(1));
        assertThat(data.hasTimer(sam.id())).isFalse();
    }

    @Test
    void startingAnotherTaskStopsAndLogsTheFirst() {
        CurrentUser sam = data.user();
        long first = data.task();
        long second = data.task();
        data.timerStartedAt(sam.id(), first, clock.instant().minusSeconds(30 * 60));

        timers.start(sam.asActor(), second, "switched");

        assertThat(data.entryCount(sam.id())).isEqualTo(1);
        assertThat(timers.running(sam.asActor())).get()
                .satisfies(t -> {
                    assertThat(t.taskId()).isEqualTo(second);
                    assertThat(t.note()).isEqualTo("switched");
                });
    }

    @Test
    void stopTimeMustBeBetweenTheStartAndNow() {
        CurrentUser sam = data.user();
        LocalDateTime start = twoWeeksAgo(9, 0);
        data.timerStartedAt(sam.id(), data.task(), instant(start));

        assertThatThrownBy(() -> timers.stop(sam.asActor(), start.minusMinutes(5)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> timers.stop(sam.asActor(), LocalDateTime.now(clock).plusHours(1)))
                .isInstanceOf(InvalidInputException.class);
        assertThat(data.hasTimer(sam.id())).isTrue();
    }

    @Test
    void ifTheTimeCantBeLoggedTheTimerSurvivesAndCanBeDiscarded() {
        CurrentUser sam = data.user();
        LocalDateTime start = twoWeeksAgo(9, 0);
        data.timerStartedAt(sam.id(), data.task(), instant(start));
        submissions.submit(sam.asActor(), sam.id(), WorkWeek.containing(start.toLocalDate()));

        assertThatThrownBy(() -> timers.stop(sam.asActor(), start.plusHours(2)))
                .isInstanceOf(NotAllowedException.class);
        assertThat(data.hasTimer(sam.id())).isTrue();
        assertThat(data.entryCount(sam.id())).isZero();

        timers.discard(sam.asActor());
        assertThat(data.hasTimer(sam.id())).isFalse();
    }

    @Test
    void cannotStartOnAnArchivedTask() {
        long task = data.task();
        data.archiveTask(task);

        assertThatThrownBy(() -> timers.start(data.user().asActor(), task, null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void headerShowsTheRunningTimerAndAForgottenOneAsksForReview() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task();

        mvc.perform(post("/timer/start").with(user(sam)).with(csrf()).param("taskId", String.valueOf(task)))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").with(user(sam)))
                .andExpect(content().string(Matchers.containsString("id=\"timer-stop\"")));

        timers.discard(sam.asActor());
        data.timerStartedAt(sam.id(), task, clock.instant().minusSeconds(16 * 3600));
        mvc.perform(get("/").with(user(sam)))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("id=\"timer-review\""),
                        Matchers.containsString("16:00"),
                        Matchers.not(Matchers.containsString("id=\"timer-stop\"")))));
    }

    @Test
    void startingWithoutATaskExplainsInsteadOfFailing() throws Exception {
        mvc.perform(post("/timer/start").with(user(data.user())).with(csrf()))
                .andExpect(flash().attribute("error", Matchers.containsString("Choose a task")));
    }
}
