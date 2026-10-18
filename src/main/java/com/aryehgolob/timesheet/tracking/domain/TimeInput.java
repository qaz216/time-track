package com.aryehgolob.timesheet.tracking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aryehgolob.timesheet.shared.InvalidInputException;

/**
 * What a user typed into a time cell, understood exactly. Never rounds.
 *
 * <pre>
 *   1:07          67 min          (h:mm)
 *   67m, 1h 7m    67 min
 *   1.5, 1.5h     90 min          (decimal hours must land on a whole minute)
 *   9:05-10:42    97 min from 09:05   (clock range, may end at 24:00, never crosses midnight)
 * </pre>
 *
 * @param minutes   exact duration, 1..1440
 * @param startTime when the user gave a clock range, otherwise null
 */
public record TimeInput(int minutes, LocalTime startTime) {

    public static final int MINUTES_PER_DAY = 24 * 60;

    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*-\\s*(\\d{1,2})(?::(\\d{2}))?");
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern UNITS = Pattern.compile("(?:(\\d+(?:\\.\\d+)?)\\s*h)?\\s*(?:(\\d+)\\s*m)?");
    private static final Pattern DECIMAL_HOURS = Pattern.compile("\\d*\\.?\\d+");

    public TimeInput {
        if (minutes < 1 || minutes > MINUTES_PER_DAY) {
            throw new InvalidInputException("Time must be between 0:01 and 24:00.");
        }
        if (startTime != null && startTime.toSecondOfDay() / 60 + minutes > MINUTES_PER_DAY) {
            throw new InvalidInputException("A time range can't cross midnight.");
        }
    }

    public static TimeInput parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase();
        if (text.isEmpty()) {
            throw new InvalidInputException("Enter a time, e.g. 1:30, 90m or 9:00-10:30.");
        }
        if (text.startsWith("-")) {
            throw new InvalidInputException("Time can't be negative.");
        }

        Matcher m;
        if ((m = RANGE.matcher(text)).matches()) {
            int from = minuteOfDay(m.group(1), m.group(2));
            int to = minuteOfDay(m.group(3), m.group(4));
            if (to <= from) {
                throw new InvalidInputException("The end time must be after the start time. Ranges can't cross midnight.");
            }
            return new TimeInput(to - from, LocalTime.of(from / 60, from % 60));
        }
        if ((m = CLOCK.matcher(text)).matches()) {
            int hours = Integer.parseInt(m.group(1));
            int mins = Integer.parseInt(m.group(2));
            if (mins > 59) {
                throw new InvalidInputException("Minutes in h:mm must be 00-59.");
            }
            return new TimeInput(hours * 60 + mins);
        }
        if (DECIMAL_HOURS.matcher(text).matches()) {
            return new TimeInput(exactMinutes(new BigDecimal(text), text));
        }
        if ((m = UNITS.matcher(text)).matches() && (m.group(1) != null || m.group(2) != null)) {
            int fromHours = m.group(1) == null ? 0 : exactMinutes(new BigDecimal(m.group(1)), m.group(1) + "h");
            int fromMinutes = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            return new TimeInput(fromHours + fromMinutes);
        }
        throw new InvalidInputException("Couldn't read \"" + raw.trim() + "\". Try 1:30, 90m, 1.5h or 9:00-10:30.");
    }

    public TimeInput(int minutes) {
        this(minutes, null);
    }

    public LocalTime endTime() {
        return startTime == null ? null : startTime.plusMinutes(minutes);
    }

    /** Back to what a user would type: "09:05-10:42" for a range, "1:37" otherwise. Parses to itself. */
    public String asTyped() {
        if (startTime == null) {
            return format(minutes);
        }
        int end = startTime.toSecondOfDay() / 60 + minutes;
        return String.format("%02d:%02d-%02d:%02d", startTime.getHour(), startTime.getMinute(), end / 60, end % 60);
    }

    /** 67 -> "1:07". The one display format used everywhere. */
    public static String format(int minutes) {
        return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
    }

    private static int exactMinutes(BigDecimal hours, String asTyped) {
        BigDecimal minutes = hours.multiply(BigDecimal.valueOf(60));
        if (minutes.stripTrailingZeros().scale() > 0) {
            int nearest = minutes.setScale(0, RoundingMode.HALF_UP).intValueExact();
            throw new InvalidInputException(
                    asTyped + " is " + minutes.stripTrailingZeros().toPlainString()
                            + " minutes, not a whole minute. Did you mean " + format(nearest) + "?");
        }
        if (minutes.compareTo(BigDecimal.valueOf(MINUTES_PER_DAY)) > 0) {
            throw new InvalidInputException("Time must be between 0:01 and 24:00.");
        }
        return minutes.intValueExact();
    }

    private static int minuteOfDay(String hours, String minutes) {
        int h = Integer.parseInt(hours);
        int m = minutes == null ? 0 : Integer.parseInt(minutes);
        if (h > 24 || m > 59 || (h == 24 && m > 0)) {
            throw new InvalidInputException("\"" + hours + (minutes == null ? "" : ":" + minutes)
                    + "\" isn't a time of day.");
        }
        return h * 60 + m;
    }
}
