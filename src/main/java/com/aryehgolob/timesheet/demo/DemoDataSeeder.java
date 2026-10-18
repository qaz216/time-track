package com.aryehgolob.timesheet.demo;

import java.util.List;

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
 * Seeds the demo accounts and projects into an empty database (dev profile
 * only), so there is someone to sign in as and something to log time against.
 * No time is seeded: every timesheet starts empty.
 */
@Component
@ConditionalOnBooleanProperty("timesheet.seed-demo-data")
class DemoDataSeeder implements ApplicationRunner {

    /** Every demo account uses this password; the sign-in page says so in the dev profile. */
    static final String DEMO_PASSWORD = "demo";

    /** Demo projects, grouped under the CTG heading in the project picker. */
    static final List<String> CTG_PROJECTS = List.of(
            "Project Administration",
            "Financial Management",
            "Budget Planning & Tracking",
            "Resource Management",
            "Vendor Management",
            "Contract Administration",
            "Billing & Invoicing",
            "Operations Management",
            "Compliance & Reporting",
            "Strategic Planning");
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
        // "demo" is an admin so one sign-in reaches everything: its own week, the team, the lock, the CSV.
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        users.save(new AppUser("demo", "demo@example.com", "Demo User", hash, Role.ADMIN));
        users.save(new AppUser("sam", "sam@example.com", "Sam Rivera", hash, Role.USER));
        users.save(new AppUser("jo", "jo@example.com", "Jo Chen", hash, Role.USER));

        // Each project is logged against directly (its hidden default task), Clockify-style.
        for (int i = 0; i < CTG_PROJECTS.size(); i++) {
            Project project = projects.save(new Project("CTG-%02d".formatted(i + 1), CTG_PROJECTS.get(i), "CTG"));
            tasks.save(Task.defaultFor(project, true));
        }

        log.info("Seeded demo accounts and projects. Sign in as demo / {}", DEMO_PASSWORD);
    }
}
