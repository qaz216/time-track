package com.aryehgolob.timesheet.tracking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.domain.TimerSplit;
import com.aryehgolob.timesheet.tracking.domain.TimerSplit.Piece;

/**
 * Start/stop timer. A timer is just a start time on the server; stopping it
 * turns it into ordinary entries through {@link TimesheetService}, so the
 * same rules, daily limit and audit apply as for typed time.
 *
 * Timers only ever belong to the actor (no ids in URLs), so there is nothing
 * to guess.
 */
@Service
@Transactional
public class TimerService {

    private final RunningTimers timers;
    private final TimesheetService timesheets;
    private final Clock clock;

    TimerService(RunningTimers timers, TimesheetService timesheets, Clock clock) {
        this.timers = timers;
        this.timesheets = timesheets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<RunningTimer> running(Actor actor) {
        return timers.find(actor.userId());
    }

    /** Starting while another timer runs stops and logs that one first: switching task is one action. */
    public void start(Actor actor, long taskId, String note) {
        timesheets.checkCanChange(actor, actor.userId(), LocalDate.now(clock));
        timesheets.requireOpenTask(taskId);
        if (timers.find(actor.userId()).isPresent()) {
            stop(actor, null);
        }
        timers.start(actor.userId(), taskId, clock.instant(), note == null || note.isBlank() ? null : note.strip());
    }

    /**
     * @param stoppedAt when the work actually ended, if not now (for a timer left
     *                  running overnight); must be between the start and now
     * @return ids of the entries created, one per calendar day (at least one: a
     *         timer stopped within its first minute logs 0:01)
     */
    public List<Long> stop(Actor actor, LocalDateTime stoppedAt) {
        RunningTimer timer = timers.find(actor.userId())
                .orElseThrow(() -> new InvalidInputException("No timer is running."));
        LocalDateTime started = LocalDateTime.ofInstant(timer.startedAt(), clock.getZone());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime end = stoppedAt == null ? now : stoppedAt;
        if (end.isBefore(started.truncatedTo(ChronoUnit.MINUTES)) || end.isAfter(now)) {
            throw new InvalidInputException("The stop time must be between when the timer started and now.");
        }

        timers.remove(actor.userId());
        // if any piece is refused (submitted week, locked period, 24h cap) the whole
        // transaction rolls back and the timer keeps running, so nothing is lost
        return TimerSplit.split(started, end).stream()
                .map((Piece piece) -> timesheets.addEntry(actor, timer.taskId(), piece.date(), piece.time(),
                        timer.note()))
                .toList();
    }

    /** Throw a timer away without logging anything. */
    public void discard(Actor actor) {
        timers.remove(actor.userId());
    }
}
