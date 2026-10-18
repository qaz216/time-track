package com.aryehgolob.timesheet.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Sign in with a username ("demo") or, for anyone who reaches for it, an email address. */
@Service
class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        String typed = login.trim();
        return users.findByUsernameIgnoreCase(typed)
                .or(() -> users.findByEmailIgnoreCase(typed))
                .map(CurrentUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
