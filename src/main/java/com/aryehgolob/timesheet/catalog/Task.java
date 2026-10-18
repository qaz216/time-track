package com.aryehgolob.timesheet.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    private String name;
    private boolean billable;
    private boolean active;

    protected Task() {
    }

    public Task(Project project, String name, boolean billable) {
        this.project = project;
        this.name = name;
        this.billable = billable;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public boolean isBillable() {
        return billable;
    }

    public boolean isActive() {
        return active;
    }
}
