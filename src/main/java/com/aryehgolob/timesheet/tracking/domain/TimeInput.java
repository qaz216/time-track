package com.aryehgolob.timesheet.tracking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aryehgolob.timesheet.shared.InvalidInputException;

/**
 * What a user typed into a time cell, understood exactly to the second. Never rounds.
 *
 * <pre>
 *   1:07, 1:07:00       1 h 7 min      (h:mm or h:mm:ss)
 *   67m, 1h 7m, 45s     as written     (any of h, m, s)
 *   1.5, 1.5h           1 h 30 min     (decimal hours must land on a whole second)
 *   9:05-10:42          1 h 37 min from 09:05   (clock range, seconds optional,
 *                                               may end at 24:00, never crosses midnight)
 * </pre>
 *
 * @param seconds   exact duration, 1..86400
 * @param startTime when the user gave a clock range (or the timer ran), otherwise null
 */
public record TimeInput(int seconds, LocalTime startTime) {

    public static final int SECONDS_PER_DAY = 24 * 60 * 60;

    private static final String CLOCK_TIME = "(\\d{1,2})(?::(\\d{2}))?(?::(\\d{2}))?";
    private static final Pattern RANGE = Pattern.compile(CLOCK_TIME + "\\s*-\\s*" + CLOCK_TIME);
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern UNITS = Pattern.compile(
            "(?:(\\d+(?:\\.\\d+)?)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?");
    private static final Pattern DECIMAL_HOURS = Pattern.compile("\\d*\\.?\\d+");
    private static final String OUT_OF_RANGE = "Time must be between 0:00:01 and 24:00:00.";

    public TimeInput {
        if (seconds < 1 || seconds > SECONDS_PER_DAY) {
            throw new InvalidInputException(OUT_OF_RANGE);
        }
        if (startTime != null && startTime.toSecondOfDay() + seconds > SECONDS_PER_DAY) {
            throw new InvalidInputException("A time range can't cross midnight.");
        }
    }

    public TimeInput(int seconds) {
        this(seconds, null);
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
            int from = secondOfDay(m.group(1), m.group(2), m.group(3));
            int to = secondOfDay(m.group(4), m.group(5), m.group(6));
            if (to <= from) {
                throw new InvalidInputException("The end time must be after the start time. Ranges can't cross midnight.");
            }
            return new TimeInput(to - from, LocalTime.ofSecondOfDay(from));
        }
        if ((m = CLOCK.matcher(text)).matches()) {
            int hours = Integer.parseInt(m.group(1));
            int mins = Integer.parseInt(m.group(2));
            int secs = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            if (mins > 59 || secs > 59) {
                throw new InvalidInputException("Minutes and seconds in h:mm:ss must be 00-59.");
            }
            return new TimeInput(hours * 3600 + mins * 60 + secs);
        }
        if (DECIMAL_HOURS.matcher(text).matches()) {
            return new TimeInput(exactSeconds(new BigDecimal(text), text));
        }
        if ((m = UNITS.matcher(text)).matches() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
            long total = (m.group(1) == null ? 0 : exactSeconds(new BigDecimal(m.group(1)), m.group(1) + "h"))
                    + (m.group(2) == null ? 0 : Long.parseLong(m.group(2)) * 60)
                    + (m.group(3) == null ? 0 : Long.parseLong(m.group(3)));
            if (total > SECONDS_PER_DAY) {
                throw new InvalidInputException(OUT_OF_RANGE);
            }
            return new TimeInput((int) total);
        }
        throw new InvalidInputException("Couldn't read \"" + raw.trim() + "\". Try 1:30, 90m, 1.5h or 9:00-10:30.");
    }

    public LocalTime endTime() {
        return startTime == null ? null : startTime.plusSeconds(seconds);
    }

    /**
     * Back to what a user would type: "09:05-10:42" for a range (with seconds
     * only when there are any), "1:37:00" otherwise. Parses to itself.
     */
    public String asTyped() {
        if (startTime == null) {
            return format(seconds);
        }
        return clock(startTime.toSecondOfDay()) + "-" + clock(startTime.toSecondOfDay() + seconds);
    }

    /** 4020 -> "1:07:00". The one display format used everywhere, like Clockify's. */
    public static String format(long seconds) {
        return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /** A time of day: "09:05", or "09:05:30" when it has seconds; midnight at the end is "24:00". */
    private static String clock(int secondOfDay) {
        int h = secondOfDay / 3600;
        int m = secondOfDay / 60 % 60;
        int s = secondOfDay % 60;
        return s == 0 ? String.format("%02d:%02d", h, m) : String.format("%02d:%02d:%02d", h, m, s);
    }

    private static int exactSeconds(BigDecimal hours, String asTyped) {
        BigDecimal seconds = hours.multiply(BigDecimal.valueOf(3600));
        if (seconds.stripTrailingZeros().scale() > 0) {
            long nearest = seconds.setScale(0, RoundingMode.HALF_UP).longValueExact();
            throw new InvalidInputException(
                    asTyped + " is " + seconds.stripTrailingZeros().toPlainString()
                            + " seconds, not a whole second. Did you mean " + format(nearest) + "?");
        }
        if (seconds.compareTo(BigDecimal.valueOf(SECONDS_PER_DAY)) > 0) {
            throw new InvalidInputException(OUT_OF_RANGE);
        }
        return seconds.intValueExact();
    }

    private static int secondOfDay(String hours, String minutes, String seconds) {
        int h = Integer.parseInt(hours);
        int m = minutes == null ? 0 : Integer.parseInt(minutes);
        int s = seconds == null ? 0 : Integer.parseInt(seconds);
        if (h > 24 || m > 59 || s > 59 || (h == 24 && (m > 0 || s > 0))) {
            String typed = hours + (minutes == null ? "" : ":" + minutes) + (seconds == null ? "" : ":" + seconds);
            throw new InvalidInputException("\"" + typed + "\" isn't a time of day.");
        }
        return h * 3600 + m * 60 + s;
    }
}
