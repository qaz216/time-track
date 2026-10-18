package com.aryehgolob.timesheet.tracking.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stopped timer into entries. Times are taken as the wall clock shows
 * them, truncated to the minute (the same thing you'd write down by hand), and
 * a timer that runs past midnight becomes one entry per day. Time is never
 * thrown away: a timer stopped within the minute it started logs 0:01, the
 * smallest amount a timesheet holds.
 */
public final class TimerSplit {

    public record Piece(LocalDate date, TimeInput time) {
    }

    private TimerSplit() {
    }

    /** @return the pieces in date order; never empty */
    public static List<Piece> split(LocalDateTime started, LocalDateTime stopped) {
        LocalDateTime from = started.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime to = stopped.truncatedTo(ChronoUnit.MINUTES);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Timer stopped before it started");
        }

        List<Piece> pieces = new ArrayList<>();
        while (from.isBefore(to)) {
            LocalDateTime midnight = from.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime pieceEnd = to.isBefore(midnight) ? to : midnight;
            int minutes = (int) Duration.between(from, pieceEnd).toMinutes();
            pieces.add(new Piece(from.toLocalDate(), new TimeInput(minutes, from.toLocalTime())));
            from = pieceEnd;
        }
        if (pieces.isEmpty()) {
            // started and stopped on the same clock minute
            pieces.add(new Piece(from.toLocalDate(), new TimeInput(1, from.toLocalTime())));
        }
        return pieces;
    }
}
