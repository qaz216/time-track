package com.aryehgolob.timesheet.tracking.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.tracking.RunningTimer;
import com.aryehgolob.timesheet.tracking.TimerService;

/** Puts the signed-in user's running timer (if any) on every page, for the header chip. */
@ControllerAdvice
class TimerModel {

    /** What the header chip and the "forgot it?" panel need. */
    record TimerView(String taskLabel, Instant startedAt, LocalDateTime startedLocal, LocalDateTime nowLocal,
            int elapsedMinutes, boolean longRunning) {
    }

    private final TimerService timers;
    private final Clock clock;

    TimerModel(TimerService timers, Clock clock) {
        this.timers = timers;
        this.clock = clock;
    }

    @ModelAttribute("timer")
    TimerView timer(@AuthenticationPrincipal CurrentUser user) {
        if (user == null) {
            return null;
        }
        Instant now = clock.instant();
        return timers.running(user.asActor())
                .map((RunningTimer t) -> new TimerView(t.taskLabel(), t.startedAt(),
                        LocalDateTime.ofInstant(t.startedAt(), clock.getZone()).truncatedTo(ChronoUnit.MINUTES),
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES),
                        t.elapsedMinutes(now), t.isLongRunning(now)))
                .orElse(null);
    }
}
