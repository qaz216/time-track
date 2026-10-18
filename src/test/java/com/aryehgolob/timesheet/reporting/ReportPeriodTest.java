package com.aryehgolob.timesheet.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

class ReportPeriodTest {

    @Test
    void widensToWholeWeeks() {
        ReportPeriod period = ReportPeriod.between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(period.start()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(period.weeks()).hasSize(5).first().isEqualTo(new WorkWeek(LocalDate.of(2026, 9, 28)));
    }

    @Test
    void aSingleDayIsItsWeek() {
        assertThat(ReportPeriod.between(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14)).weeks())
                .containsExactly(new WorkWeek(LocalDate.of(2026, 10, 12)));
    }

    @Test
    void refusesABackwardsRange() {
        assertThatThrownBy(() -> ReportPeriod.between(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void allowsAYearButNotMore() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        assertThat(ReportPeriod.between(start, start.plusWeeks(52)).weeks()).hasSize(53);

        assertThatThrownBy(() -> ReportPeriod.between(start, start.plusWeeks(53)))
                .isInstanceOf(InvalidInputException.class);
    }
}
