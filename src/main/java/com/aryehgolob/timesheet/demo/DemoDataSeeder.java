package com.aryehgolob.timesheet.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aryehgolob.timesheet.catalog.Project;
import com.aryehgolob.timesheet.catalog.ProjectRepository;
import com.aryehgolob.timesheet.catalog.Task;
import com.aryehgolob.timesheet.catalog.TaskRepository;
import com.aryehgolob.timesheet.identity.AppUser;
import com.aryehgolob.timesheet.identity.AppUserRepository;
import com.aryehgolob.timesheet.identity.Role;

/**
 * Seeds demo users and projects into an empty database (dev profile only).
 * Done in Java rather than SQL so passwords go through the real encoder.
 */
@Component
@ConditionalOnBooleanProperty("timesheet.seed-demo-data")
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo1234";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final PasswordEncoder passwordEncoder;

    DemoDataSeeder(AppUserRepository users, ProjectRepository projects, TaskRepository tasks,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        users.save(new AppUser("admin@example.com", "Avery Admin", hash, Role.ADMIN));
        users.save(new AppUser("sam@example.com", "Sam Rivera", hash, Role.USER));
        users.save(new AppUser("jo@example.com", "Jo Chen", hash, Role.USER));

        Project acme = projects.save(new Project("ACME-WEB", "Website rebuild", "Acme Corp"));
        tasks.save(new Task(acme, "Development", true));
        tasks.save(new Task(acme, "Meetings", true));

        Project globex = projects.save(new Project("GLOBEX-DATA", "Data migration", "Globex"));
        tasks.save(new Task(globex, "Migration", true));
        tasks.save(new Task(globex, "Analysis", true));

        Project internal = projects.save(new Project("INTERNAL", "Internal", "Our company"));
        tasks.save(new Task(internal, "Admin", false));
        tasks.save(new Task(internal, "Training", false));

        log.info("Seeded demo data. Log in as admin@example.com / {}", DEMO_PASSWORD);
    }
}
