package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeInputTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} min")
    @CsvSource(delimiter = '|', textBlock = """
            1:07     | 67
            0:01     | 1
            24:00    | 1440
            67m      | 67
            1h 7m    | 67
            1h7m     | 67
            2h       | 120
            1.5      | 90
            1.5h     | 90
            .25      | 15
            0.05     | 3
            8        | 480
            '  1:30 '| 90
            1H 30M   | 90
            """)
    void readsDurationsExactly(String typed, int minutes) {
        TimeInput input = TimeInput.parse(typed);

        assertThat(input.minutes()).isEqualTo(minutes);
        assertThat(input.startTime()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" -> {2} min from {1}")
    @CsvSource(delimiter = '|', textBlock = """
            9:05-10:42   | 09:05 | 97
            9:05 - 10:42 | 09:05 | 97
            9-17         | 09:00 | 480
            23:10-24:00  | 23:10 | 50
            0:00-0:01    | 00:00 | 1
            """)
    void readsClockRanges(String typed, LocalTime start, int minutes) {
        TimeInput input = TimeInput.parse(typed);

        assertThat(input.startTime()).isEqualTo(start);
        assertThat(input.minutes()).isEqualTo(minutes);
    }

    @Test
    void decimalHoursThatAreNotWholeMinutesAreRejectedWithASuggestion() {
        assertThatThrownBy(() -> TimeInput.parse("1.33"))
                .isInstanceOf(InvalidTimeInputException.class)
                .hasMessageContaining("79.8 minutes")
                .hasMessageContaining("Did you mean 1:20?");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @CsvSource(delimiter = '|', textBlock = """
            ''           | Enter a time
            0            | between 0:01 and 24:00
            0:00         | between 0:01 and 24:00
            -1           | negative
            25h          | between 0:01 and 24:00
            24:01        | between 0:01 and 24:00
            1:60         | 00-59
            10:00-9:00   | after the start
            9:00-9:00    | after the start
            22:00-1:00   | after the start
            9:00-25:00   | isn't a time of day
            abc          | Couldn't read
            1,5          | Couldn't read
            """)
    void rejectsNonsenseWithAHelpfulMessage(String typed, String messagePart) {
        assertThatThrownBy(() -> TimeInput.parse(typed))
                .isInstanceOf(InvalidTimeInputException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void aRangeCannotBeConstructedAcrossMidnight() {
        assertThatThrownBy(() -> new TimeInput(120, LocalTime.of(23, 0)))
                .isInstanceOf(InvalidTimeInputException.class)
                .hasMessageContaining("midnight");
    }

    @Test
    void endTimeIsDerivedSoItCanNeverDisagreeWithTheDuration() {
        assertThat(TimeInput.parse("9:05-10:42").endTime()).isEqualTo(LocalTime.of(10, 42));
        assertThat(TimeInput.parse("1:30").endTime()).isNull();
    }

    @ParameterizedTest(name = "{0} min -> {1}")
    @CsvSource({"1, 0:01", "67, 1:07", "600, 10:00", "1440, 24:00"})
    void formatsAsHoursAndMinutes(int minutes, String shown) {
        assertThat(TimeInput.format(minutes)).isEqualTo(shown);
    }

    @Test
    void dailyLimitAllowsExactly24HoursAndNoMore() {
        DailyLimit.check(1380, 60);

        assertThatThrownBy(() -> DailyLimit.check(1380, 61))
                .isInstanceOf(InvalidTimeInputException.class)
                .hasMessageContaining("24:01");
    }
}
