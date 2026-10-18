package com.aryehgolob.timesheet.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String client;
    private boolean active;

    protected Project() {
    }

    /** @param client may be null: the project then sits at the top level of the picker */
    public Project(String code, String name, String client) {
        this.code = code;
        this.name = name;
        this.client = client;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getClient() {
        return client;
    }

    public boolean isActive() {
        return active;
    }
}
