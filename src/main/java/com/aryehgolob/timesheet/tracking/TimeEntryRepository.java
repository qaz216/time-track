package com.aryehgolob.timesheet.tracking;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    @Query("select coalesce(sum(e.minutes), 0) from TimeEntry e where e.userId = :userId and e.workDate = :date")
    long minutesOn(long userId, LocalDate date);

    @Query("""
            select coalesce(sum(e.minutes), 0) from TimeEntry e
            where e.userId = :userId and e.workDate between :from and :to
            """)
    long minutesBetween(long userId, LocalDate from, LocalDate to);
}
