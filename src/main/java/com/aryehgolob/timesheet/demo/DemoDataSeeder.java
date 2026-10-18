package com.aryehgolob.timesheet.demo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;

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

        Project acme = projects.save(new Project("ACME-WEB", "Website rebuild", "Acme Corp"));
        Task acmeDev = tasks.save(new Task(acme, "Development", true));
        Task acmeMeetings = tasks.save(new Task(acme, "Meetings", true));

        Project globex = projects.save(new Project("GLOBEX-DATA", "Data migration", "Globex"));
        Task migration = tasks.save(new Task(globex, "Migration", true));
        Task analysis = tasks.save(new Task(globex, "Analysis", true));

        Project internal = projects.save(new Project("INTERNAL", "Internal", "Our company"));
        tasks.save(new Task(internal, "Admin", false));
        Task training = tasks.save(new Task(internal, "Training", false));

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
                timesheets.addEntry(samActor, acmeDev.getId(), day, "9:00-12:30", null);
                timesheets.addEntry(samActor, acmeMeetings.getId(), day, "12:30-13:00", "Standup");
                timesheets.addEntry(samActor, acmeDev.getId(), day, "13:30-17:15", null);
                timesheets.addEntry(joActor, migration.getId(), day, "6:45", null);
                timesheets.addEntry(joActor, day.getDayOfWeek() == DayOfWeek.FRIDAY ? training.getId()
                        : analysis.getId(), day, "1:15", null);
            }
        }
        submissions.submit(samActor, sam.getId(), thisWeek.previous().previous());
        submissions.submit(samActor, sam.getId(), thisWeek.previous());
        submissions.submit(joActor, jo.getId(), thisWeek.previous().previous());

        log.info("Seeded demo data. Log in as admin@example.com / {}", DEMO_PASSWORD);
    }
}
