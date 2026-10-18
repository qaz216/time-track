package com.aryehgolob.timesheet.tracking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aryehgolob.timesheet.tracking.domain.TimeInput;

/**
 * One block of time against a task on a day. Who and which day are fixed at
 * creation (to move it to another day, delete and re-add), which keeps the edit
 * rules about one date and one week. The task, time and note can change.
 */
@Entity
@Table(name = "time_entry")
class TimeEntry {

    /** What the audit log stores as "before" and "after". */
    record Snapshot(long userId, long taskId, LocalDate workDate, int seconds, LocalTime startTime, String note) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long taskId;
    private LocalDate workDate;
    private int seconds;
    private LocalTime startTime;
    private String note;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected TimeEntry() {
    }

    TimeEntry(long userId, long taskId, LocalDate workDate, TimeInput time, String note, Instant now) {
        this.userId = userId;
        this.taskId = taskId;
        this.workDate = workDate;
        this.seconds = time.seconds();
        this.startTime = time.startTime();
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @return false if nothing actually changed (so nothing is written or audited) */
    boolean change(long taskId, TimeInput time, String note, Instant now) {
        if (this.taskId == taskId && seconds == time.seconds() && Objects.equals(startTime, time.startTime())
                && Objects.equals(this.note, note)) {
            return false;
        }
        this.taskId = taskId;
        this.seconds = time.seconds();
        this.startTime = time.startTime();
        this.note = note;
        this.updatedAt = now;
        return true;
    }

    Snapshot snapshot() {
        return new Snapshot(userId, taskId, workDate, seconds, startTime, note);
    }

    Long getId() {
        return id;
    }

    long getUserId() {
        return userId;
    }

    long getTaskId() {
        return taskId;
    }

    LocalDate getWorkDate() {
        return workDate;
    }

    int getSeconds() {
        return seconds;
    }

    int getVersion() {
        return version;
    }
}
