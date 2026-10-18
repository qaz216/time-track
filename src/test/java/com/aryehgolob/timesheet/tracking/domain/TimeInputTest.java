package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.aryehgolob.timesheet.shared.InvalidInputException;

class TimeInputTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} s")
    @CsvSource(delimiter = '|', textBlock = """
            1:07       | 4020
            0:01       | 60
            1:30:15    | 5415
            0:00:01    | 1
            24:00      | 86400
            24:00:00   | 86400
            67m        | 4020
            1h 7m      | 4020
            1h7m       | 4020
            45s        | 45
            1m 30s     | 90
            1h 2m 3s   | 3723
            2h         | 7200
            1.5        | 5400
            1.5h       | 5400
            1.33       | 4788
            .25        | 900
            8          | 28800
            '  1:30 '  | 5400
            1H 30M     | 5400
            """)
    void readsDurationsExactly(String typed, int seconds) {
        TimeInput input = TimeInput.parse(typed);

        assertThat(input.seconds()).isEqualTo(seconds);
        assertThat(input.startTime()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" -> {2} s from {1}")
    @CsvSource(delimiter = '|', textBlock = """
            9:05-10:42        | 09:05    | 5820
            9:05 - 10:42      | 09:05    | 5820
            9-17              | 09:00    | 28800
            23:10-24:00       | 23:10    | 3000
            0:00-0:01         | 00:00    | 60
            9:05:30-9:06      | 09:05:30 | 30
            9:00:00-9:00:45   | 09:00    | 45
            """)
    void readsClockRanges(String typed, LocalTime start, int seconds) {
        TimeInput input = TimeInput.parse(typed);

        assertThat(input.startTime()).isEqualTo(start);
        assertThat(input.seconds()).isEqualTo(seconds);
    }

    @Test
    void decimalHoursThatAreNotWholeSecondsAreRejectedWithASuggestion() {
        assertThatThrownBy(() -> TimeInput.parse("1.00001"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("3600.036 seconds")
                .hasMessageContaining("Did you mean 1:00:00?");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @CsvSource(delimiter = '|', textBlock = """
            ''            | Enter a time
            0             | between 0:00:01 and 24:00:00
            0:00          | between 0:00:01 and 24:00:00
            0s            | between 0:00:01 and 24:00:00
            -1            | negative
            25h           | between 0:00:01 and 24:00:00
            24:01         | between 0:00:01 and 24:00:00
            24:00:01      | between 0:00:01 and 24:00:00
            86401s        | between 0:00:01 and 24:00:00
            1:60          | 00-59
            1:00:60       | 00-59
            10:00-9:00    | after the start
            9:00-9:00     | after the start
            22:00-1:00    | after the start
            9:00-25:00    | isn't a time of day
            9:00:61-10:00 | isn't a time of day
            abc           | Couldn't read
            1,5           | Couldn't read
            """)
    void rejectsNonsenseWithAHelpfulMessage(String typed, String messagePart) {
        assertThatThrownBy(() -> TimeInput.parse(typed))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void aRangeCannotBeConstructedAcrossMidnight() {
        assertThatThrownBy(() -> new TimeInput(7200, LocalTime.of(23, 0)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("midnight");
    }

    @Test
    void endTimeIsDerivedSoItCanNeverDisagreeWithTheDuration() {
        assertThat(TimeInput.parse("9:05-10:42").endTime()).isEqualTo(LocalTime.of(10, 42));
        assertThat(TimeInput.parse("1:30").endTime()).isNull();
    }

    @ParameterizedTest(name = "{0} s -> {1}")
    @CsvSource({"1, 0:00:01", "47, 0:00:47", "4020, 1:07:00", "36000, 10:00:00", "86400, 24:00:00"})
    void formatsAsHoursMinutesSeconds(int seconds, String shown) {
        assertThat(TimeInput.format(seconds)).isEqualTo(shown);
    }

    @ParameterizedTest(name = "\"{0}\" shows as \"{1}\" and reads back the same")
    @CsvSource(delimiter = '|', textBlock = """
            9:05-10:42      | 09:05-10:42
            9:05:30-10:42   | 09:05:30-10:42
            23:10-24:00     | 23:10-24:00
            1:37            | 1:37:00
            45s             | 0:00:45
            """)
    void asTypedRoundTrips(String typed, String shown) {
        TimeInput input = TimeInput.parse(typed);

        assertThat(input.asTyped()).isEqualTo(shown);
        assertThat(TimeInput.parse(input.asTyped())).isEqualTo(input);
    }

    @Test
    void dailyLimitAllowsExactly24HoursAndNoMore() {
        DailyLimit.check(82800, 3600);

        assertThatThrownBy(() -> DailyLimit.check(82800, 3601))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("24:00:01");
    }
}
