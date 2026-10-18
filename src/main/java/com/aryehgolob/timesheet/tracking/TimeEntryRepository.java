package com.aryehgolob.timesheet.tracking;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    @Query("select coalesce(sum(e.seconds), 0) from TimeEntry e where e.userId = :userId and e.workDate = :date")
    long secondsOn(long userId, LocalDate date);

    @Query("""
            select coalesce(sum(e.seconds), 0) from TimeEntry e
            where e.userId = :userId and e.workDate between :from and :to
            """)
    long secondsBetween(long userId, LocalDate from, LocalDate to);
}
