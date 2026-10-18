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

    static final LocalDate WED = LocalDate.of(2026, 10, 14);

    @Test
    void sameDayTimerBecomesOneEntryExactToTheSecond() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T09:05:40"), at("2026-10-14T10:42:10"));

        assertThat(pieces).containsExactly(new Piece(WED, new TimeInput(5790, LocalTime.of(9, 5, 40))));
    }

    @Test
    void secondsCountExactlyNotByClockMinute() {
        // 10:00:59 -> 10:01:01 crosses a minute on the clock, but it's two seconds of work
        assertThat(TimerSplit.split(at("2026-10-14T10:00:59"), at("2026-10-14T10:01:01")))
                .singleElement().extracting(p -> p.time().seconds()).isEqualTo(2);
    }

    @Test
    void underAMinuteLogsExactlyWhatRan() {
        assertThat(TimerSplit.split(at("2026-10-14T10:00:05"), at("2026-10-14T10:00:55")))
                .containsExactly(new Piece(WED, new TimeInput(50, LocalTime.of(10, 0, 5))));
    }

    @Test
    void withinTheSameSecondStillLogsOneSecondNothingIsThrownAway() {
        assertThat(TimerSplit.split(at("2026-10-14T10:00:05.100"), at("2026-10-14T10:00:05.900")))
                .containsExactly(new Piece(WED, new TimeInput(1, LocalTime.of(10, 0, 5))));
    }

    @Test
    void theLastSecondBeforeMidnightIsAllowed() {
        assertThat(TimerSplit.split(at("2026-10-14T23:59:59.200"), at("2026-10-14T23:59:59.800")))
                .containsExactly(new Piece(WED, new TimeInput(1, LocalTime.of(23, 59, 59))));
    }

    @Test
    void forgottenOvernightIsSplitAtMidnight() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T22:30:00"), at("2026-10-15T08:15:00"));

        assertThat(pieces).containsExactly(
                new Piece(WED, new TimeInput(5400, LocalTime.of(22, 30))),
                new Piece(WED.plusDays(1), new TimeInput(29700, LocalTime.MIDNIGHT)));
    }

    @Test
    void aWholeDayInTheMiddleBecomesA24HourPiece() {
        List<Piece> pieces = TimerSplit.split(at("2026-10-14T23:00:00"), at("2026-10-16T01:00:00"));

        assertThat(pieces).extracting(p -> p.time().seconds()).containsExactly(3600, 86400, 3600);
        assertThat(pieces).extracting(Piece::date).containsExactly(WED, WED.plusDays(1), WED.plusDays(2));
    }

    @Test
    void secondsAfterMidnightBelongToTheNewDay() {
        assertThat(TimerSplit.split(at("2026-10-14T23:00:00"), at("2026-10-15T00:00:30")))
                .extracting(p -> p.time().seconds()).containsExactly(3600, 30);
    }

    @Test
    void stoppingExactlyAtMidnightDoesNotCreateAnEmptyPiece() {
        assertThat(TimerSplit.split(at("2026-10-14T23:00:00"), at("2026-10-15T00:00:00")))
                .singleElement().extracting(p -> p.time().seconds()).isEqualTo(3600);
    }

    @Test
    void refusesAStopBeforeTheStart() {
        assertThatThrownBy(() -> TimerSplit.split(at("2026-10-14T10:00"), at("2026-10-14T09:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
