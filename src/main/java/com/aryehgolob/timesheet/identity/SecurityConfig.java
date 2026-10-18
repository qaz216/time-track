package com.aryehgolob.timesheet.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Form login + server-side session + CSRF. No tokens: there is no SPA or API
 * client that would need them. Swapping to company SSO (OIDC) would replace
 * formLogin with oauth2Login and leave everything else alone.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // bcrypt today, with a {id} prefix so hashes can be upgraded later
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
