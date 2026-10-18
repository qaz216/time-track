package com.aryehgolob.timesheet.tracking.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aryehgolob.timesheet.shared.InvalidInputException;

/**
 * A time of day the way people type it into a start or end box, Clockify-style:
 *
 * <pre>
 *   9, 9am, 9 a.m.        09:00
 *   915, 0915, 9:15       09:15
 *   115pm, 1:15 PM        13:15
 *   1315, 13:15, 13:15:30 13:15(:30)
 *   12am / 12pm           midnight / noon
 *   24:00                 end of the day (only meaningful as an end)
 * </pre>
 *
 * Works in seconds of the day, 0..86400, so the end of the day fits.
 */
public final class ClockTime {

    public static final int END_OF_DAY = TimeInput.SECONDS_PER_DAY;

    private static final Pattern WITH_COLONS = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,6}");

    private ClockTime() {
    }

    /** @return seconds since midnight, 0..86400 */
    public static int parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase().replace(" ", "").replace(".", "");
        Boolean pm = null;
        if (text.endsWith("am") || text.endsWith("pm")) {
            pm = text.endsWith("pm");
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("a") || text.endsWith("p")) {
            pm = text.endsWith("p");
            text = text.substring(0, text.length() - 1);
        }

        int h;
        int m = 0;
        int s = 0;
        Matcher colons = WITH_COLONS.matcher(text);
        if (colons.matches()) {
            h = Integer.parseInt(colons.group(1));
            m = Integer.parseInt(colons.group(2));
            s = colons.group(3) == null ? 0 : Integer.parseInt(colons.group(3));
        } else if (DIGITS.matcher(text).matches()) {
            // 9 -> 9, 915 -> 9:15, 1315 -> 13:15, 91530 -> 9:15:30, 131530 -> 13:15:30
            int hourDigits = text.length() % 2 == 1 ? 1 : 2;
            if (text.length() <= 2) {
                hourDigits = text.length();
            }
            h = Integer.parseInt(text.substring(0, hourDigits));
            String rest = text.substring(hourDigits);
            m = rest.length() >= 2 ? Integer.parseInt(rest.substring(0, 2)) : 0;
            s = rest.length() >= 4 ? Integer.parseInt(rest.substring(2, 4)) : 0;
        } else {
            throw notATime(raw);
        }

        if (m > 59 || s > 59) {
            throw notATime(raw);
        }
        if (pm != null) {
            if (h < 1 || h > 12) {
                throw notATime(raw);
            }
            h = h % 12 + (pm ? 12 : 0);
        } else if (h > 24 || (h == 24 && (m > 0 || s > 0))) {
            throw notATime(raw);
        }
        return h * 3600 + m * 60 + s;
    }

    /** "1:15 PM", or "1:15:30 PM" when there are seconds. The end of the day shows as "12:00 AM". */
    public static String format(int secondOfDay) {
        int h = secondOfDay / 3600 % 24;
        int m = secondOfDay / 60 % 60;
        int s = secondOfDay % 60;
        String clock = (h % 12 == 0 ? 12 : h % 12) + ":" + String.format("%02d", m);
        if (s != 0) {
            clock += ":" + String.format("%02d", s);
        }
        return clock + (h < 12 ? " AM" : " PM");
    }

    /** "1:15 PM" without seconds, for lists, as Clockify shows them. */
    public static String formatShort(int secondOfDay) {
        return format(secondOfDay - secondOfDay % 60);
    }

    private static InvalidInputException notATime(String raw) {
        return new InvalidInputException("\"" + (raw == null ? "" : raw.trim())
                + "\" isn't a time of day. Try 9:15, 915 or 1:15 PM.");
    }
}
