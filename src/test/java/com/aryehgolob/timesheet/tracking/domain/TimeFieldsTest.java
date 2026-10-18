package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.aryehgolob.timesheet.shared.InvalidInputException;

class TimeFieldsTest {

    @ParameterizedTest(name = "start \"{0}\", end \"{1}\", duration \"{2}\" -> {4} s from {3}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            9:00     | 10:30    | -        | 09:00    | 5400
            115pm    | 245pm    | -        | 13:15    | 5400
            9:00     | 10:30    | 0:01:00  | 09:00    | 5400
            9:00     | -        | 1:30     | 09:00    | 5400
            -        | 10:30    | 1:30     | 09:00    | 5400
            1:43:43 PM | 1:43:49 PM | -    | 13:43:43 | 6
            11:10 PM | 12:00 AM | -        | 23:10    | 3000
            -        | -        | 9:00-10:30 | 09:00  | 5400
            """)
    void startEndAndDurationResolveToARange(String start, String end, String duration, LocalTime from, int seconds) {
        TimeInput time = TimeFields.resolve(start, end, duration).orElseThrow();

        assertThat(time.startTime()).isEqualTo(from);
        assertThat(time.seconds()).isEqualTo(seconds);
    }

    @Test
    void aDurationOnItsOwnHasNoClockTimes() {
        TimeInput time = TimeFields.resolve("", " ", "1:30").orElseThrow();

        assertThat(time.seconds()).isEqualTo(5400);
        assertThat(time.startTime()).isNull();
    }

    @Test
    void nothingAtAllIsEmptySoTheBarCanStartATimer() {
        assertThat(TimeFields.resolve(null, "", "  ")).isEmpty();
    }

    @Test
    void theUntouchedDefaultsStartEqualsEndCountAsNoTime() {
        assertThat(TimeFields.resolve("2:21 PM", "2:21 PM", "")).isEmpty();
        assertThat(TimeFields.resolve("2:21 PM", "2:21 PM", "0:00:00")).isEmpty();
    }

    @Test
    void aDurationWithTheDefaultStartRunsFromTheStart() {
        TimeInput time = TimeFields.resolve("2:21 PM", "2:21 PM", "1:30").orElseThrow();

        assertThat(time.startTime()).isEqualTo(LocalTime.of(14, 21));
        assertThat(time.seconds()).isEqualTo(5400);
    }

    @ParameterizedTest(name = "start \"{0}\", end \"{1}\", duration \"{2}\" -> \"{3}\"")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            10:30 | 9:00  | -    | end must be after the start
            9:00  | -     | -    | Add an end time or a duration
            -     | 10:30 | -    | Add an end time or a duration
            -     | 1:00  | 2:00 | start before midnight
            11 PM | -     | 2:00 | can't cross midnight
            24:00 | -     | 1:00 | can't start at the end of the day
            9:75  | -     | 1:00 | isn't a time of day
            9:00  | -     | abc  | Couldn't read
            """)
    void explainsWhatIsWrong(String start, String end, String duration, String message) {
        assertThatThrownBy(() -> TimeFields.resolve(start, end, duration))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining(message);
    }
}
