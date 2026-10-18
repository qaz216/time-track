package com.aryehgolob.timesheet;

import org.junit.jupiter.api.Test;

import com.aryehgolob.timesheet.support.IntegrationTest;

/** Boots the whole app against real Postgres: Flyway runs, Hibernate validates the schema. */
class TimesheetApplicationTests extends IntegrationTest {

    @Test
    void schemaMigratesAndMatchesEntities() {
        // context startup is the assertion
    }
}
