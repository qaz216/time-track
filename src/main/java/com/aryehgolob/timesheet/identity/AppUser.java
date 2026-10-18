package com.aryehgolob.timesheet.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What people sign in with, e.g. "demo". Stored lower-case. */
    private String username;
    private String email;
    private String displayName;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean active;

    protected AppUser() {
    }

    public AppUser(String username, String email, String displayName, String passwordHash, Role role) {
        this.username = username.trim().toLowerCase();
        this.email = email.trim().toLowerCase();
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
    }

    /** People who leave keep their history; they just can't sign in. */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
