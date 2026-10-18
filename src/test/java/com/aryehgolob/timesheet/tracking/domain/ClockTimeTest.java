package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.aryehgolob.timesheet.shared.InvalidInputException;

class ClockTimeTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource(delimiter = '|', textBlock = """
            9           | 09:00
            09          | 09:00
            9am         | 09:00
            9 a.m.      | 09:00
            9a          | 09:00
            9p          | 21:00
            915         | 09:15
            0915        | 09:15
            9:15        | 09:15
            115pm       | 13:15
            1:15PM      | 13:15
            1:15 pm     | 13:15
            1315        | 13:15
            13:15       | 13:15
            13:15:30    | 13:15:30
            1:15:30 PM  | 13:15:30
            91530       | 09:15:30
            12am        | 00:00
            12pm        | 12:00
            12:30 AM    | 00:30
            0           | 00:00
            """)
    void readsTimesThePeopleTypeThem(String typed, LocalTime expected) {
        assertThat(ClockTime.parse(typed)).isEqualTo(expected.toSecondOfDay());
    }

    @ParameterizedTest(name = "\"{0}\" is the end of the day")
    @CsvSource({"24", "24:00", "24:00:00"})
    void twentyFourHundredIsTheEndOfTheDay(String typed) {
        assertThat(ClockTime.parse(typed)).isEqualTo(ClockTime.END_OF_DAY);
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @CsvSource(delimiter = '|', textBlock = """
            ''
            abc
            25
            24:01
            9:60
            960
            13pm
            0am
            1:15:61
            1234567
            """)
    void rejectsWhatIsNotATimeOfDay(String typed) {
        assertThatThrownBy(() -> ClockTime.parse(typed))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("isn't a time of day");
    }

    @ParameterizedTest(name = "{0} shows as \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            09:00     | 9:00 AM
            13:15     | 1:15 PM
            13:15:30  | 1:15:30 PM
            00:00     | 12:00 AM
            12:00     | 12:00 PM
            23:59:59  | 11:59:59 PM
            """)
    void formatsOnATwelveHourClock(LocalTime time, String shown) {
        assertThat(ClockTime.format(time.toSecondOfDay())).isEqualTo(shown);
        assertThat(ClockTime.parse(shown)).isEqualTo(time.toSecondOfDay());
    }

    @Test
    void theEndOfTheDayShowsAsMidnightAndListsDropSeconds() {
        assertThat(ClockTime.format(ClockTime.END_OF_DAY)).isEqualTo("12:00 AM");
        assertThat(ClockTime.formatShort(LocalTime.of(13, 43, 49).toSecondOfDay())).isEqualTo("1:43 PM");
    }
}
