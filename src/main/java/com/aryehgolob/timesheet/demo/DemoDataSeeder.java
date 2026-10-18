package com.aryehgolob.timesheet.demo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.aryehgolob.timesheet.identity.Actor;
import com.aryehgolob.timesheet.identity.AppUser;
import com.aryehgolob.timesheet.identity.AppUserRepository;
import com.aryehgolob.timesheet.identity.Role;
import com.aryehgolob.timesheet.tracking.TimesheetService;
import com.aryehgolob.timesheet.tracking.WeekSubmissionService;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * Seeds demo users, projects and three weeks of time into an empty database
 * (dev profile only). Time goes through the real services, so it is validated
 * and audited like anything a user types.
 */
@Component
@ConditionalOnBooleanProperty("timesheet.seed-demo-data")
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo1234";

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
    private final TimesheetService timesheets;
    private final WeekSubmissionService submissions;
    private final Clock clock;

    DemoDataSeeder(AppUserRepository users, ProjectRepository projects, TaskRepository tasks,
            PasswordEncoder passwordEncoder, TimesheetService timesheets, WeekSubmissionService submissions,
            Clock clock) {
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.passwordEncoder = passwordEncoder;
        this.timesheets = timesheets;
        this.submissions = submissions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        users.save(new AppUser("admin@example.com", "Avery Admin", hash, Role.ADMIN));
        AppUser sam = users.save(new AppUser("sam@example.com", "Sam Rivera", hash, Role.USER));
        AppUser jo = users.save(new AppUser("jo@example.com", "Jo Chen", hash, Role.USER));

        // Each project is logged against directly (its hidden default task), Clockify-style.
        Map<String, Task> task = new HashMap<>();
        for (int i = 0; i < CTG_PROJECTS.size(); i++) {
            String name = CTG_PROJECTS.get(i);
            Project project = projects.save(new Project("CTG-%02d".formatted(i + 1), name, "CTG"));
            task.put(name, tasks.save(Task.defaultFor(project, true)));
        }
        long projectAdmin = task.get("Project Administration").getId();
        long operations = task.get("Operations Management").getId();
        long finance = task.get("Financial Management").getId();
        long budget = task.get("Budget Planning & Tracking").getId();
        long compliance = task.get("Compliance & Reporting").getId();

        // Two finished weeks plus this week so far. Sam submitted both; Jo forgot one.
        Actor samActor = new Actor(sam.getId(), false);
        Actor joActor = new Actor(jo.getId(), false);
        LocalDate today = LocalDate.now(clock);
        WorkWeek thisWeek = WorkWeek.containing(today);
        for (WorkWeek week : new WorkWeek[] {thisWeek.previous().previous(), thisWeek.previous(), thisWeek}) {
            for (LocalDate day : week.days()) {
                if (!day.isBefore(today) || day.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) {
                    continue;
                }
                timesheets.addEntry(samActor, projectAdmin, day, "9:00-12:30", null);
                timesheets.addEntry(samActor, operations, day, "12:30-13:00", "Standup");
                timesheets.addEntry(samActor, projectAdmin, day, "13:30-17:15", null);
                timesheets.addEntry(joActor, finance, day, "6:45", null);
                timesheets.addEntry(joActor, day.getDayOfWeek() == DayOfWeek.FRIDAY ? compliance : budget,
                        day, "1:15", null);
            }
        }
        submissions.submit(samActor, sam.getId(), thisWeek.previous().previous());
        submissions.submit(samActor, sam.getId(), thisWeek.previous());
        submissions.submit(joActor, jo.getId(), thisWeek.previous().previous());

        log.info("Seeded demo data. Log in as admin@example.com / {}", DEMO_PASSWORD);
    }
}
