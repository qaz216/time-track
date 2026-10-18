package com.aryehgolob.timesheet.identity;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user as the rest of the app sees it. Carries the id so services
 * can check ownership without another database lookup.
 */
public record CurrentUser(Long id, String username, String email, String displayName, Role role, String passwordHash,
        boolean active)
        implements UserDetails {

    static CurrentUser from(AppUser user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getPasswordHash(), user.isActive());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Actor asActor() {
        return new Actor(id, isAdmin());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return "CurrentUser[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}
