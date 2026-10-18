package com.aryehgolob.timesheet.catalog;

/**
 * A stable colour per project, derived from its id so there's nothing to store
 * or manage. Golden-angle hue steps keep neighbouring projects distinct.
 */
public final class ProjectColour {

    private ProjectColour() {
    }

    public static String of(long projectId) {
        return "hsl(" + Math.round((projectId * 137.508) % 360) + " 58% 46%)";
    }
}
