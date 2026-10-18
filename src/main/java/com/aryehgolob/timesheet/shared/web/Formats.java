package com.aryehgolob.timesheet.shared.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.aryehgolob.timesheet.tracking.domain.TimeInput;

/** Display formats, used from templates as {@code ${@fmt.minutes(m)}}. */
@Component("fmt")
public class Formats {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public String minutes(int minutes) {
        return minutes == 0 ? "" : TimeInput.format(minutes);
    }

    public String total(int minutes) {
        return TimeInput.format(minutes);
    }

    public String day(LocalDate date) {
        return DAY.format(date);
    }

    public String shortDay(LocalDate date) {
        return SHORT_DAY.format(date);
    }

    public String date(LocalDate date) {
        return LONG_DATE.format(date);
    }
}
