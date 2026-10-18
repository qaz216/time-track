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
 * Seeds an empty database (dev profile only) with the demo accounts, the CTG
 * projects, and four weeks of sample time for Sam and Jo, shaped so the team
 * view shows every state: submitted, draft, and nothing logged. The demo
 * user's own timesheet starts empty. Time goes through the real services, so
 * it's validated and audited like anything typed.
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

    /** One sample entry: a project, a time as a user would type it, and a description. */
    private record Slot(String project, String time, String note) {
    }

    /** Sam, a project lead: clock ranges, a different shape each weekday. */
    private static final Map<DayOfWeek, List<Slot>> SAM = Map.of(
            DayOfWeek.MONDAY, List.of(
                    new Slot("Operations Management", "9:00-10:00", "Weekly ops sync"),
                    new Slot("Project Administration", "10:00-12:30", "Status reports"),
                    new Slot("Resource Management", "13:30-17:00", "Staffing plan for Q1")),
            DayOfWeek.TUESDAY, List.of(
                    new Slot("Contract Administration", "8:45-12:15", "Redline MSA with vendor"),
                    new Slot("Vendor Management", "13:00-14:00", "Vendor check-in call"),
                    new Slot("Project Administration", "14:00-16:45", "Schedule updates")),
            DayOfWeek.WEDNESDAY, List.of(
                    new Slot("Billing & Invoicing", "9:15-11:45", "Prepare client invoices"),
                    new Slot("Operations Management", "11:45-12:15", "Standup"),
                    new Slot("Project Administration", "13:00-17:30", "Risk register review")),
            DayOfWeek.THURSDAY, List.of(
                    new Slot("Strategic Planning", "9:00-12:00", "Roadmap workshop"),
                    new Slot("Resource Management", "13:00-15:20", "Interview candidates"),
                    new Slot("Compliance & Reporting", "15:20-16:50", "Audit evidence")),
            DayOfWeek.FRIDAY, List.of(
                    new Slot("Budget Planning & Tracking", "9:30-11:00", "Budget variance review"),
                    new Slot("Operations Management", "11:00-12:00", "Team retro"),
                    new Slot("Project Administration", "13:00-15:00", "Weekly status report")));

    /** Jo, in finance: mostly plain durations, typed at the end of the day. */
    private static final Map<DayOfWeek, List<Slot>> JO = Map.of(
            DayOfWeek.MONDAY, List.of(
                    new Slot("Financial Management", "6:30", "Month-end close"),
                    new Slot("Compliance & Reporting", "1:00", "SOX checklist")),
            DayOfWeek.TUESDAY, List.of(
                    new Slot("Financial Management", "4:15", "Accounts reconciliation"),
                    new Slot("Budget Planning & Tracking", "3:00", "Forecast model")),
            DayOfWeek.WEDNESDAY, List.of(
                    new Slot("Billing & Invoicing", "2:45", "Invoice run"),
                    new Slot("Financial Management", "4:30", "Cash flow report")),
            DayOfWeek.THURSDAY, List.of(
                    new Slot("Budget Planning & Tracking", "9:00-12:30", "Department budgets"),
                    new Slot("Financial Management", "13:30-17:00", "Variance analysis")),
            DayOfWeek.FRIDAY, List.of(
                    new Slot("Compliance & Reporting", "3:20", "Quarterly filing prep"),
                    new Slot("Financial Management", "2:10", null)));

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
        // "demo" is an admin so one sign-in reaches everything: its own week, the team, the lock, the CSV.
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        users.save(new AppUser("demo", "demo@example.com", "Demo User", hash, Role.ADMIN));
        AppUser sam = users.save(new AppUser("sam", "sam@example.com", "Sam Rivera", hash, Role.USER));
        AppUser jo = users.save(new AppUser("jo", "jo@example.com", "Jo Chen", hash, Role.USER));

        // Each project is logged against directly (its hidden default task), Clockify-style.
        Map<String, Long> taskOf = new HashMap<>();
        for (int i = 0; i < CTG_PROJECTS.size(); i++) {
            Project project = projects.save(new Project("CTG-%02d".formatted(i + 1), CTG_PROJECTS.get(i), "CTG"));
            taskOf.put(project.getName(), tasks.save(Task.defaultFor(project, true)).getId());
        }

        seedSampleTime(new Actor(sam.getId(), false), new Actor(jo.getId(), false), taskOf);
        log.info("Seeded demo accounts, projects and sample time for Sam and Jo. Sign in as demo / {}",
                DEMO_PASSWORD);
    }

    /**
     * Four weeks ending yesterday, chosen so Team weeks shows each state:
     *
     * <pre>
     *            3 weeks ago   2 weeks ago   last week        this week
     *   Sam      submitted     submitted     submitted (a     draft
     *                                        short Thursday)
     *   Jo       submitted     draft         nothing logged   Mon and Tue so far
     * </pre>
     */
    private void seedSampleTime(Actor sam, Actor jo, Map<String, Long> taskOf) {
        LocalDate today = LocalDate.now(clock);
        WorkWeek thisWeek = WorkWeek.containing(today);
        WorkWeek[] weeks = {thisWeek.previous().previous().previous(), thisWeek.previous().previous(),
                thisWeek.previous(), thisWeek};

        for (int w = 0; w < weeks.length; w++) {
            for (LocalDate day : weeks[w].days()) {
                if (!day.isBefore(today) || !SAM.containsKey(day.getDayOfWeek())) {
                    continue;   // only weekdays that have already happened
                }
                List<Slot> samsDay = SAM.get(day.getDayOfWeek());
                if (w == 2 && day.getDayOfWeek() == DayOfWeek.THURSDAY) {
                    samsDay = samsDay.subList(0, 1);   // a half day last week
                }
                log(sam, day, samsDay, taskOf);

                boolean joLogs = w <= 1 || (w == 3 && day.getDayOfWeek().getValue() <= 2);
                if (joLogs) {
                    log(jo, day, JO.get(day.getDayOfWeek()), taskOf);
                }
            }
        }
        // a quick call, timed to the second, on the most recent weekday of this week so far
        thisWeek.days().stream()
                .filter(day -> day.isBefore(today) && SAM.containsKey(day.getDayOfWeek()))
                .reduce((first, second) -> second)
                .ifPresent(day -> timesheets.addEntry(sam, taskOf.get("Vendor Management"), day, "0:07:42",
                        "Quick vendor call"));

        for (int w = 0; w <= 2; w++) {
            submissions.submit(sam, sam.userId(), weeks[w]);
        }
        submissions.submit(jo, jo.userId(), weeks[0]);
    }

    private void log(Actor who, LocalDate day, List<Slot> slots, Map<String, Long> taskOf) {
        for (Slot slot : slots) {
            timesheets.addEntry(who, taskOf.get(slot.project()), day, slot.time(), slot.note());
        }
    }
}
