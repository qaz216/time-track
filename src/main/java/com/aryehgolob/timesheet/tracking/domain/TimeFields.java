package com.aryehgolob.timesheet.tracking.domain;

import java.time.LocalTime;
import java.util.Optional;

import com.aryehgolob.timesheet.shared.InvalidInputException;

/**
 * The start / end / duration boxes of the entry bar and edit row, resolved into
 * one {@link TimeInput}, the way Clockify treats them:
 *
 * <ul>
 *   <li>start + end: the range between them (an end of 12:00 AM means midnight)</li>
 *   <li>start + duration, or end + duration: the range they imply</li>
 *   <li>duration alone: just a duration (it also accepts "9:00-10:30")</li>
 *   <li>nothing at all, or start = end with no duration (the bar's untouched
 *       defaults): empty, and the caller decides (the bar starts a timer)</li>
 * </ul>
 *
 * When all three are filled, start and end win: the page keeps duration in step anyway.
 */
public final class TimeFields {

    private TimeFields() {
    }

    public static Optional<TimeInput> resolve(String start, String end, String duration) {
        Integer from = blank(start) ? null : ClockTime.parse(start);
        Integer to = blank(end) ? null : ClockTime.parse(end);
        TimeInput length = blank(duration) || isZero(duration) ? null : TimeInput.parse(duration);

        if (from != null && from == ClockTime.END_OF_DAY) {
            throw new InvalidInputException("An entry can't start at the end of the day.");
        }
        // The bar opens with start = end = now, as Clockify does. Left like that, no
        // time was given (the bar starts a timer); with a duration, the start is what counts.
        if (from != null && from.equals(to)) {
            if (length == null) {
                return Optional.empty();
            }
            to = null;
        }
        if (to != null && to == 0 && from != null && from > 0) {
            to = ClockTime.END_OF_DAY;   // 12:00 AM as an end is the coming midnight
        }

        if (from != null && to != null) {
            if (to <= from) {
                throw new InvalidInputException(
                        "The end must be after the start. An entry can't cross midnight: split it into two days.");
            }
            return Optional.of(new TimeInput(to - from, LocalTime.ofSecondOfDay(from)));
        }
        if (length != null && length.startTime() != null && from == null && to == null) {
            return Optional.of(length);   // "9:00-10:30" typed into the duration box
        }
        if (from != null && length != null) {
            return Optional.of(new TimeInput(length.seconds(), LocalTime.ofSecondOfDay(from)));
        }
        if (to != null && length != null) {
            int begins = to - length.seconds();
            if (begins < 0) {
                throw new InvalidInputException("That would start before midnight. Split it into two days.");
            }
            return Optional.of(new TimeInput(length.seconds(), LocalTime.ofSecondOfDay(begins)));
        }
        if (length != null) {
            return Optional.of(length);
        }
        if (from != null || to != null) {
            throw new InvalidInputException("Add an end time or a duration.");
        }
        return Optional.empty();
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /** "0:00:00", "0" and friends: the empty duration box's own value, not a duration. */
    private static boolean isZero(String text) {
        return text.strip().matches("[0:.]+");
    }
}
