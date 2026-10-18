package com.aryehgolob.timesheet.support;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aryehgolob.timesheet.identity.AppUser;
import com.aryehgolob.timesheet.identity.AppUserRepository;
import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.identity.Role;

/** Creates uniquely named fixtures so tests never collide on shared data. */
@TestComponent
public class TestData {

    public static final String PASSWORD = "correct horse";
    private static final AtomicInteger sequence = new AtomicInteger();

    private final AppUserRepository users;
    private final UserDetailsService userDetails;
    private final String passwordHash;

    TestData(AppUserRepository users, UserDetailsService userDetails, PasswordEncoder encoder) {
        this.users = users;
        this.userDetails = userDetails;
        this.passwordHash = encoder.encode(PASSWORD);
    }

    public CurrentUser user() {
        return create(Role.USER);
    }

    public CurrentUser admin() {
        return create(Role.ADMIN);
    }

    public CurrentUser inactiveUser() {
        AppUser user = new AppUser(uniqueEmail(), "Former Employee", passwordHash, Role.USER);
        user.deactivate();
        users.save(user);
        return (CurrentUser) userDetails.loadUserByUsername(user.getEmail());
    }

    private CurrentUser create(Role role) {
        AppUser user = users.save(new AppUser(uniqueEmail(), "Test " + role, passwordHash, role));
        return (CurrentUser) userDetails.loadUserByUsername(user.getEmail());
    }

    private static String uniqueEmail() {
        return "user" + sequence.incrementAndGet() + "-" + System.nanoTime() + "@test.example";
    }
}
