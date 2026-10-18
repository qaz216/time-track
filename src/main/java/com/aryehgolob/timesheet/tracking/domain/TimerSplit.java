package com.aryehgolob.timesheet.tracking.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stopped timer into entries, exact to the second, with one entry per
 * calendar day when it runs past midnight. Times are wall-clock times in the
 * office zone. Time is never thrown away: a timer stopped within the second it
 * started logs 0:00:01.
 */
public final class TimerSplit {

    public record Piece(LocalDate date, TimeInput time) {
    }

    private TimerSplit() {
    }

    /** @return the pieces in date order; never empty */
    public static List<Piece> split(LocalDateTime started, LocalDateTime stopped) {
        LocalDateTime from = started.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime to = stopped.truncatedTo(ChronoUnit.SECONDS);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Timer stopped before it started");
        }

        List<Piece> pieces = new ArrayList<>();
        while (from.isBefore(to)) {
            LocalDateTime midnight = from.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime pieceEnd = to.isBefore(midnight) ? to : midnight;
            int seconds = (int) Duration.between(from, pieceEnd).toSeconds();
            pieces.add(new Piece(from.toLocalDate(), new TimeInput(seconds, from.toLocalTime())));
            from = pieceEnd;
        }
        if (pieces.isEmpty()) {
            // started and stopped within the same second
            pieces.add(new Piece(from.toLocalDate(), new TimeInput(1, from.toLocalTime())));
        }
        return pieces;
    }
}
