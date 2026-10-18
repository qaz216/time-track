package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aryehgolob.timesheet.tracking.domain.TimerSplit.Piece;

class TimerSplitTest {

    static LocalDateTime at(String iso) {
        return LocalDateTime.parse(iso);
    }

    @Test
    void sameDayTimerBecomesOneEntryWithItsClockTimes() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T09:05:40"), at("2026-10-14T10:42:10"));

        assertThat(pieces).containsExactly(
                new Piece(LocalDate.of(2026, 10, 14), new TimeInput(97, LocalTime.of(9, 5))));
    }

    @Test
    void secondsAreDroppedTheWayAWallClockWouldShowThem() {
        // 10:00:59 -> 10:01:01 is two seconds, but the clock moved from 10:00 to 10:01
        assertThat(TimerSplit.split(at("2026-10-14T10:00:59"), at("2026-10-14T10:01:01")))
                .singleElement().extracting(p -> p.time().minutes()).isEqualTo(1);
    }

    @Test
    void underAMinuteStillLogsOneMinuteNothingIsThrownAway() {
        assertThat(TimerSplit.split(at("2026-10-14T10:00:05"), at("2026-10-14T10:00:55")))
                .containsExactly(new Piece(LocalDate.of(2026, 10, 14), new TimeInput(1, LocalTime.of(10, 0))));
    }

    @Test
    void aFewSecondsJustBeforeMidnightIsTheDaysLastMinute() {
        assertThat(TimerSplit.split(at("2026-10-14T23:59:10"), at("2026-10-14T23:59:50")))
                .containsExactly(new Piece(LocalDate.of(2026, 10, 14), new TimeInput(1, LocalTime.of(23, 59))));
    }

    @Test
    void forgottenOvernightIsSplitAtMidnight() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T22:30:00"), at("2026-10-15T08:15:00"));

        assertThat(pieces).containsExactly(
                new Piece(LocalDate.of(2026, 10, 14), new TimeInput(90, LocalTime.of(22, 30))),
                new Piece(LocalDate.of(2026, 10, 15), new TimeInput(495, LocalTime.MIDNIGHT)));
    }

    @Test
    void aWholeDayInTheMiddleBecomesA24HourPiece() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T23:00:00"), at("2026-10-16T01:00:00"));

        assertThat(pieces).extracting(p -> p.time().minutes()).containsExactly(60, 1440, 60);
        assertThat(pieces).extracting(Piece::date).containsExactly(
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));
    }

    @Test
    void stoppingExactlyAtMidnightDoesNotCreateAnEmptyPiece() {
        assertThat(TimerSplit.split(at("2026-10-14T23:00:00"), at("2026-10-15T00:00:30")))
                .singleElement().extracting(p -> p.time().minutes()).isEqualTo(60);
    }

    @Test
    void refusesAStopBeforeTheStart() {
        assertThatThrownBy(() -> TimerSplit.split(at("2026-10-14T10:00"), at("2026-10-14T09:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
