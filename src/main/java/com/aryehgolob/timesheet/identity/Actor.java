package com.aryehgolob.timesheet.identity;

/** Who is asking, as the domain rules need to know it. Free of any Spring types. */
public record Actor(long userId, boolean admin) {

    public boolean is(long otherUserId) {
        return userId == otherUserId;
    }
}
