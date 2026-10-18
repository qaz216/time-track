package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkWeekTest {

    @ParameterizedTest(name = "{0} is in the week of {1}")
    @CsvSource({
            "2026-10-12, 2026-10-12",   // Monday is its own week
            "2026-10-18, 2026-10-12",   // Sunday belongs to the week before it
            "2026-10-14, 2026-10-12",
            "2027-01-01, 2026-12-28",   // year rollover: Friday 1 Jan
            "2026-03-01, 2026-02-23",   // Sunday 1 March, leap-free February
            "2028-02-29, 2028-02-28",   // leap day
    })
    void findsTheMondayOfAnyDate(LocalDate date, LocalDate monday) {
        assertThat(WorkWeek.containing(date).start()).isEqualTo(monday);
    }

    @Test
    void hasSevenDaysEndingOnSunday() {
        WorkWeek week = WorkWeek.containing(LocalDate.of(2026, 12, 30));

        assertThat(week.days()).hasSize(7).first().isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(week.end()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(week.contains(LocalDate.of(2027, 1, 3))).isTrue();
        assertThat(week.contains(LocalDate.of(2027, 1, 4))).isFalse();
        assertThat(week.contains(LocalDate.of(2026, 12, 27))).isFalse();
    }

    @Test
    void stepsAcrossYears() {
        WorkWeek week = new WorkWeek(LocalDate.of(2026, 12, 28));

        assertThat(week.next().start()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(week.next().previous()).isEqualTo(week);
    }

    @Test
    void refusesAWeekThatDoesNotStartOnMonday() {
        assertThatThrownBy(() -> new WorkWeek(LocalDate.of(2026, 10, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
