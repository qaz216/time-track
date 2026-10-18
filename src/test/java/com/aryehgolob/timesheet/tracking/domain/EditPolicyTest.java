package com.aryehgolob.timesheet.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.period.PeriodLock;
import com.aryehgolob.timesheet.tracking.domain.Decision.Denial;

class EditPolicyTest {

    static final long OWNER = 1;
    static final long SOMEONE_ELSE = 2;
    static final long ADMIN = 3;

    static final WorkWeek WEEK = new WorkWeek(LocalDate.of(2026, 10, 12));
    static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

    static Actor actor(String who) {
        return switch (who) {
            case "owner" -> new Actor(OWNER, false);
            case "other" -> new Actor(SOMEONE_ELSE, false);
            case "admin" -> new Actor(ADMIN, true);
            case "owner-admin" -> new Actor(OWNER, true);
            default -> throw new IllegalArgumentException(who);
        };
    }

    /** null = nothing locked; otherwise the locked-through date. */
    static PeriodLock lock(LocalDate through) {
        return through == null ? PeriodLock.NONE : new PeriodLock(through);
    }

    @Nested
    class ChangingAnEntry {

        @ParameterizedTest(name = "{0}, submitted={1}, locked through {2} -> {3}")
        @CsvSource(nullValues = "-", textBlock = """
                owner,       false, -,          ALLOWED
                owner,       true,  -,          WEEK_SUBMITTED
                other,       false, -,          NOT_OWNER
                admin,       false, -,          NOT_OWNER
                owner-admin, false, -,          ALLOWED
                owner,       false, 2026-10-14, PERIOD_LOCKED
                owner,       false, 2026-10-13, ALLOWED
                admin,       false, 2026-10-31, PERIOD_LOCKED
                owner,       true,  2026-10-31, PERIOD_LOCKED
                other,       true,  -,          NOT_OWNER
                """)
        void matrix(String who, boolean submitted, LocalDate lockedThrough, String expected) {
            Decision decision = EditPolicy.canChangeEntry(actor(who), OWNER, WEDNESDAY, submitted, lock(lockedThrough));

            assertThat(outcome(decision)).isEqualTo(expected);
        }
    }

    @Nested
    class SubmittingAWeek {

        @ParameterizedTest(name = "{0}, submitted={1}, locked through {2} -> {3}")
        @CsvSource(nullValues = "-", textBlock = """
                owner, false, -,          ALLOWED
                owner, true,  -,          ALREADY_SUBMITTED
                other, false, -,          NOT_OWNER
                admin, false, -,          NOT_OWNER
                owner, false, 2026-10-14, ALLOWED
                owner, false, 2026-10-18, PERIOD_LOCKED
                """)
        void matrix(String who, boolean submitted, LocalDate lockedThrough, String expected) {
            Decision decision = EditPolicy.canSubmit(actor(who), OWNER, WEEK, submitted, lock(lockedThrough));

            assertThat(outcome(decision)).isEqualTo(expected);
        }
    }

    @Nested
    class ReopeningAWeek {

        @ParameterizedTest(name = "{0}, submitted={1}, locked through {2} -> {3}")
        @CsvSource(nullValues = "-", textBlock = """
                owner, true,  -,          ALLOWED
                admin, true,  -,          ALLOWED
                other, true,  -,          NOT_OWNER
                owner, false, -,          NOT_SUBMITTED
                owner, true,  2026-10-14, ALLOWED
                admin, true,  2026-10-18, PERIOD_LOCKED
                """)
        void matrix(String who, boolean submitted, LocalDate lockedThrough, String expected) {
            Decision decision = EditPolicy.canReopen(actor(who), OWNER, WEEK, submitted, lock(lockedThrough));

            assertThat(outcome(decision)).isEqualTo(expected);
        }
    }

    @Test
    void deniedDecisionThrowsWithAMessageForTheUser() {
        Decision decision = EditPolicy.canChangeEntry(actor("owner"), OWNER, WEDNESDAY, true, PeriodLock.NONE);

        assertThatThrownBy(decision::orThrow)
                .isInstanceOf(NotAllowedException.class)
                .hasMessage(Denial.WEEK_SUBMITTED.message());
    }

    private static String outcome(Decision decision) {
        return decision.isAllowed() ? "ALLOWED" : decision.denial().name();
    }
}
