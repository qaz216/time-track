package com.aryehgolob.timesheet.demo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

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
 * and Amazon projects with their tasks, and four weeks of sample time: Sam
 * and Jo by hand, the rest of the team generated. Sam and Jo are shaped so
 * the team view shows every state: submitted, draft, and nothing logged. The
 * demo user's own timesheet starts empty. Time goes through the real
 * services, so it's validated and audited like anything typed.
 */
@Component
@ConditionalOnBooleanProperty("timesheet.seed-demo-data")
class DemoDataSeeder implements ApplicationRunner {

    /** Every demo account uses this password; the sign-in page says so in the dev profile. */
    static final String DEMO_PASSWORD = "demo";

    /** The CTG project's tasks. */
    static final List<String> CTG_TASKS = List.of(
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

    /** The Amazon project's tasks. */
    static final List<String> AMAZON_TASKS = List.of(
            "Discovery",
            "UX Design",
            "Development",
            "QA & Testing",
            "Deployment");

    /** Everyone else on the team; usernames are the first names, lowercased. */
    static final List<String> TEAM = List.of(
            "Alex Morgan", "Priya Patel", "Marcus Johnson", "Elena Rossi", "David Kim",
            "Fatima Hassan", "Liam O'Brien", "Sofia Garcia", "Noah Williams", "Aisha Bello",
            "Ethan Brooks", "Mei Lin", "Daniel Cohen", "Grace Okafor", "Lucas Silva",
            "Hannah Weiss", "Omar Haddad", "Chloe Martin", "Ravi Iyer", "Zoe Turner");

    /** A few believable notes for each task, for the generated team time. */
    private static final Map<String, List<String>> NOTES = Map.ofEntries(
            Map.entry("Project Administration", List.of("Status reports", "Schedule updates", "Project kickoff prep")),
            Map.entry("Financial Management", List.of("Month-end close", "Accounts reconciliation", "Cash flow report")),
            Map.entry("Budget Planning & Tracking", List.of("Forecast model", "Budget variance review", "Q1 budget draft")),
            Map.entry("Resource Management", List.of("Staffing plan", "Interview candidates", "Capacity review")),
            Map.entry("Vendor Management", List.of("Vendor check-in call", "Review vendor quotes", "Supplier scorecard")),
            Map.entry("Contract Administration", List.of("Redline MSA", "Renewal paperwork", "Contract review")),
            Map.entry("Billing & Invoicing", List.of("Invoice run", "Prepare client invoices", "Chase overdue invoices")),
            Map.entry("Operations Management", List.of("Standup", "Weekly ops sync", "Process improvements")),
            Map.entry("Compliance & Reporting", List.of("Audit evidence", "SOX checklist", "Quarterly filing prep")),
            Map.entry("Strategic Planning", List.of("Roadmap workshop", "Market research", "Planning offsite prep")),
            Map.entry("Discovery", List.of("Requirements workshop", "Stakeholder interviews", "Current-state review")),
            Map.entry("UX Design", List.of("Checkout wireframes", "Usability test notes", "Design review")),
            Map.entry("Development", List.of("Checkout API integration", "Order service refactor", "Code review")),
            Map.entry("QA & Testing", List.of("Regression test pass", "Test plan", "Bug triage")),
            Map.entry("Deployment", List.of("Release prep", "Staging deploy", "Release notes")));

    /** One sample entry: a task, a time as a user would type it, and a description. */
    private record Slot(String task, String time, String note) {
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
                    new Slot("Development", "13:00-17:30", "Checkout API integration")),
            DayOfWeek.THURSDAY, List.of(
                    new Slot("Discovery", "9:00-12:00", "Requirements workshop"),
                    new Slot("Resource Management", "13:00-15:20", "Interview candidates"),
                    new Slot("Compliance & Reporting", "15:20-16:50", "Audit evidence")),
            DayOfWeek.FRIDAY, List.of(
                    new Slot("Budget Planning & Tracking", "9:30-11:00", "Budget variance review"),
                    new Slot("Operations Management", "11:00-12:00", "Team retro"),
                    new Slot("QA & Testing", "13:00-15:00", "Regression test pass")));

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

        // Two projects with no client, so the picker lists them at the top level, each with its tasks.
        Map<String, Long> taskOf = new HashMap<>();
        addProject("CTG", "CTG", CTG_TASKS, taskOf);
        addProject("AMZ", "Amazon", AMAZON_TASKS, taskOf);

        seedSampleTime(new Actor(sam.getId(), false), new Actor(jo.getId(), false), taskOf);
        seedTeam(hash, taskOf);
        log.info("Seeded demo accounts, the CTG and Amazon projects, and sample time for {} people. Sign in as demo / {}",
                TEAM.size() + 2, DEMO_PASSWORD);
    }

    /**
     * The rest of the team, so Team weeks looks like a real firm: each person
     * works mostly on a few tasks, logs a 6 to 8.5 hour day in a few entries,
     * and usually submits on time. Now and then someone forgets to submit or
     * takes a week off. A fixed seed per person (from the name) keeps the data
     * the same on every fresh start.
     */
    private void seedTeam(String hash, Map<String, Long> taskOf) {
        LocalDate today = LocalDate.now(clock);
        WorkWeek[] weeks = sampleWeeks(today);
        List<String> allTasks = new ArrayList<>(CTG_TASKS);
        allTasks.addAll(AMAZON_TASKS);

        for (int p = 0; p < TEAM.size(); p++) {
            String name = TEAM.get(p);
            String username = name.substring(0, name.indexOf(' ')).toLowerCase(Locale.ROOT);
            AppUser user = users.save(new AppUser(username, username + "@example.com", name, hash, Role.USER));
            Actor who = new Actor(user.getId(), false);
            Random rnd = new Random(name.hashCode());   // not p: neighbouring seeds start out alike
            List<String> usual = new ArrayList<>(allTasks);
            Collections.shuffle(usual, rnd);
            usual = usual.subList(0, 3 + rnd.nextInt(3));

            for (int w = 0; w < weeks.length; w++) {
                if (w < weeks.length - 1 && rnd.nextInt(12) == 0) {
                    continue;   // a week off
                }
                boolean logged = false;
                for (LocalDate day : weeks[w].days()) {
                    if (!day.isBefore(today) || day.getDayOfWeek().getValue() > 5) {
                        continue;
                    }
                    int left = 6 * 60 + rnd.nextInt(11) * 15;
                    int pieces = 2 + rnd.nextInt(3);
                    for (int i = 0; i < pieces && left > 0; i++) {
                        int minutes = i == pieces - 1 ? left : Math.min(left, 30 + rnd.nextInt(10) * 15);
                        String task = usual.get(rnd.nextInt(usual.size()));
                        List<String> notes = NOTES.get(task);
                        timesheets.addEntry(who, taskOf.get(task), day, "%d:%02d".formatted(minutes / 60, minutes % 60),
                                notes.get(rnd.nextInt(notes.size())));
                        left -= minutes;
                    }
                    logged = true;
                }
                // past weeks: most people have submitted, the odd one forgot; this week is still open
                boolean submits = rnd.nextInt(10) < (w == weeks.length - 2 ? 7 : 9);
                if (logged && w < weeks.length - 1 && submits) {
                    submissions.submit(who, user.getId(), weeks[w]);
                }
            }
        }
    }

    /** Three weeks back through this week. */
    private static WorkWeek[] sampleWeeks(LocalDate today) {
        WorkWeek thisWeek = WorkWeek.containing(today);
        return new WorkWeek[] {thisWeek.previous().previous().previous(), thisWeek.previous().previous(),
                thisWeek.previous(), thisWeek};
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
        WorkWeek[] weeks = sampleWeeks(today);
        WorkWeek thisWeek = weeks[3];

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

    private void addProject(String code, String name, List<String> taskNames, Map<String, Long> taskOf) {
        Project project = projects.save(new Project(code, name, null));
        for (String taskName : taskNames) {
            taskOf.put(taskName, tasks.save(new Task(project, taskName, true)).getId());
        }
    }

    private void log(Actor who, LocalDate day, List<Slot> slots, Map<String, Long> taskOf) {
        for (Slot slot : slots) {
            timesheets.addEntry(who, taskOf.get(slot.task()), day, slot.time(), slot.note());
        }
    }
}
