package com.aryehgolob.timesheet.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.tracking.TimesheetService;

class WeekPageTest extends IntegrationTest {

    static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

    @Autowired
    TimesheetService timesheets;

    @Test
    void addingAnEntryRedirectsToItsWeekAndShowsIt() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(task))
                        .param("date", WEDNESDAY.toString())
                        .param("start", "905").param("end", "10:42 am")
                        .param("note", "Kickoff"))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("9:05 AM - 10:42 AM")))
                .andExpect(content().string(containsString("1:37:00")))
                .andExpect(content().string(containsString("Kickoff")));
    }

    @Test
    void theEntryBoxOnlyGrabsFocusRightAfterAnAddSoShortcutsWorkOnAFreshPage() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(get("/").with(user(sam)))
                .andExpect(content().string(not(containsString("autofocus"))));

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task()))
                        .param("date", WEDNESDAY.toString())
                        .param("duration", "1:00"))
                .andExpect(flash().attribute("focusEntry", true));
    }

    @Test
    void aProjectWithNoClientIsOfferedWithItsTasksAndLabelledProjectThenTask() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task("Amazon", null, "Development");
        timesheets.addEntry(sam.asActor(), task, WEDNESDAY, "2:00", null);

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<optgroup>")))
                .andExpect(content().string(containsString("data-task=\"Development\"")))
                .andExpect(content().string(containsString("Amazon › Development")));
    }

    @Test
    void projectsLoggedDirectlyShowJustTheProjectNameAndNamedTasksShowBoth() throws Exception {
        CurrentUser sam = data.user();
        timesheets.addEntry(sam.asActor(), data.projectTask("Strategic Planning"), WEDNESDAY, "1:00", null);
        timesheets.addEntry(sam.asActor(), data.task("Website", "Acme", "Design"), WEDNESDAY, "1:00", null);

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(content().string(containsString(">Strategic Planning<")))
                .andExpect(content().string(not(containsString("Strategic Planning › General"))))
                .andExpect(content().string(containsString(">Website › Design<")));
    }

    @Test
    void dayListShowsOnlyDaysWithTimeNewestFirst() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task();
        timesheets.addEntry(sam.asActor(), task, LocalDate.of(2031, 3, 3), "1:00", null);  // Monday
        timesheets.addEntry(sam.asActor(), task, LocalDate.of(2031, 3, 5), "2:00", null);  // Wednesday

        String page = mvc.perform(get("/").param("week", "2031-03-03").with(user(sam)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Wed 5 Mar", "Mon 3 Mar").doesNotContain("Tue 4 Mar", "Sun 9 Mar");
        assertThat(page.indexOf("Wed 5 Mar")).isLessThan(page.indexOf("Mon 3 Mar"));
    }

    @Test
    void badInputComesBackAsAMessageWithWhatWasTyped() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task()))
                        .param("date", WEDNESDAY.toString())
                        .param("duration", "1.00001"))
                .andExpect(redirectedUrl("/?week=2026-10-12"))
                .andExpect(flash().attribute("error", containsString("Did you mean 1:00:00?")));
        assertThat(data.entryCount(sam.id())).isZero();
    }

    @Test
    void addingWithoutAProjectAsksForOneInsteadOfFailing() throws Exception {
        mvc.perform(post("/entries").with(user(data.user())).with(csrf())
                        .param("date", WEDNESDAY.toString()).param("duration", "1:00").param("note", "kept"))
                .andExpect(redirectedUrl("/?week=2026-10-12"))
                .andExpect(flash().attribute("error", "Pick a project."))
                .andExpect(flash().attribute("form", new EntryForm(null, WEDNESDAY, "", "", "1:00", "kept")));
    }

    @Test
    void anyDayCanBePickedAndThePageFollowsTheEntryToItsWeek() throws Exception {
        CurrentUser sam = data.user();
        LocalDate aMonthEarlier = LocalDate.of(2026, 9, 16);

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task())).param("week", "2026-10-12")
                        .param("date", aMonthEarlier.toString()).param("duration", "1:00"))
                .andExpect(redirectedUrl("/?week=2026-09-14"));

        assertThat(data.entryCount(sam.id())).isOne();
    }

    @Test
    void addWithStartAndDurationWorksOutTheEnd() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task())).param("date", WEDNESDAY.toString())
                        .param("start", "1:15pm").param("duration", "1:30"))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(content().string(containsString("1:15 PM - 2:45 PM")));
    }

    @Test
    void addWithOnlyAStartTimeAsksForTheRestAndKeepsWhatWasTyped() throws Exception {
        long task = data.task();

        mvc.perform(post("/entries").with(user(data.user())).with(csrf())
                        .param("taskId", String.valueOf(task)).param("date", WEDNESDAY.toString())
                        .param("start", "9:00"))
                .andExpect(flash().attribute("error", containsString("Add an end time or a duration")))
                .andExpect(flash().attribute("form", new EntryForm(task, WEDNESDAY, "9:00", "", "", null)));
    }

    @Test
    void editingMovesTheTimesAndCanChangeTheProject() throws Exception {
        CurrentUser sam = data.user();
        long first = data.task();
        long second = data.projectTask("Vendor Management");
        long id = timesheets.addEntry(sam.asActor(), first, WEDNESDAY, "1:43:43pm-1:43:49pm", "Test");

        mvc.perform(post("/entries/{id}", id).with(user(sam)).with(csrf())
                        .param("version", "0").param("week", "2026-10-12").param("taskId", String.valueOf(second))
                        .param("start", "1:00 PM").param("end", "2:30 PM").param("duration", "0:00:06")
                        .param("note", "Test"))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        var row = data.entry(id);
        assertThat(row.get("seconds")).isEqualTo(5400);   // start and end win over a stale duration
        assertThat(row.get("task_id")).isEqualTo(second);
    }

    @Test
    void editingWithAnUntouchedProjectKeepsIt() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task();
        long id = timesheets.addEntry(sam.asActor(), task, WEDNESDAY, "1:00", null);

        mvc.perform(post("/entries/{id}", id).with(user(sam)).with(csrf())
                        .param("version", "0").param("week", "2026-10-12").param("taskId", "")
                        .param("duration", "2:00"))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        assertThat(data.entry(id).get("task_id")).isEqualTo(task);
        assertThat(data.entry(id).get("seconds")).isEqualTo(7200);
    }

    @Test
    void addWithNoTimeStartsATimerFromNowInsteadOfLoggingAnything() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task())).param("note", "Client call")
                        .param("date", WEDNESDAY.toString()).param("duration", "  "))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("notice", containsString("timer started")));

        assertThat(data.hasTimer(sam.id())).isTrue();
        assertThat(data.entryCount(sam.id())).isZero();
        mvc.perform(get("/").with(user(sam)))
                .andExpect(content().string(containsString("Client call")))
                .andExpect(content().string(containsString("data-running=\"true\"")));
    }

    @Test
    void addWithTheUntouchedDefaultTimesStartsATimerToo() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task())).param("date", WEDNESDAY.toString())
                        .param("start", "2:21 PM").param("end", "2:21 PM").param("duration", ""))
                .andExpect(flash().attribute("notice", containsString("timer started")));

        assertThat(data.hasTimer(sam.id())).isTrue();
        assertThat(data.entryCount(sam.id())).isZero();
    }

    @Test
    void addWithNoTimeWhileATimerRunsLogsTheOldOneAndStartsAgain() throws Exception {
        CurrentUser sam = data.user();
        long task = data.task();
        data.timerStartedAt(sam.id(), task, java.time.Instant.now().minusSeconds(45 * 60));

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(task)).param("duration", ""))
                .andExpect(flash().attribute("notice", containsString("previous timer")));

        assertThat(data.entryCount(sam.id())).isEqualTo(1);
        assertThat(data.hasTimer(sam.id())).isTrue();
    }

    @Test
    void addWithNoTimeStillNeedsAProject() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf()).param("duration", ""))
                .andExpect(flash().attribute("error", "Pick a project."));

        assertThat(data.hasTimer(sam.id())).isFalse();
    }

    @Test
    void addingToASubmittedDayIsAMessageNotAnErrorPage() throws Exception {
        CurrentUser sam = data.user();
        data.submit(sam.id(), LocalDate.of(2026, 10, 12));

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task()))
                        .param("date", WEDNESDAY.toString()).param("duration", "1:00"))
                .andExpect(redirectedUrl("/?week=2026-10-12"))
                .andExpect(flash().attribute("error", containsString("submitted")));
    }

    @Test
    void userCannotDeleteAnotherUsersEntryByGuessingItsId() throws Exception {
        CurrentUser sam = data.user();
        CurrentUser jo = data.user();
        long samsEntry = timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", null);

        mvc.perform(post("/entries/{id}/delete", samsEntry).with(user(jo)).with(csrf())
                        .param("week", "2026-10-12"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/entries/{id}", samsEntry).with(user(jo)).with(csrf())
                        .param("version", "0").param("duration", "8:00").param("week", "2026-10-12"))
                .andExpect(status().isForbidden());

        assertThat(data.entry(samsEntry).get("seconds")).isEqualTo(3600);
    }

    @Test
    void changesWithoutACsrfTokenAreRejected() throws Exception {
        CurrentUser sam = data.user();
        long id = timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", null);

        mvc.perform(post("/entries/{id}/delete", id).with(user(sam)).param("week", "2026-10-12"))
                .andExpect(status().isForbidden());

        assertThat(data.entryCount(sam.id())).isOne();
    }

    @Test
    void submittedWeekShowsNoEntryFormOrEditButtons() throws Exception {
        CurrentUser sam = data.user();
        timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", "visible");
        data.submit(sam.id(), LocalDate.of(2026, 10, 12));

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("visible")))
                .andExpect(content().string(containsString("submitted and read-only")))
                .andExpect(content().string(not(containsString("id=\"quick-add\""))))
                .andExpect(content().string(not(containsString(">Edit<"))));
    }

    @Test
    void submitButtonSubmitsTheWeekAndOffersReopen() throws Exception {
        CurrentUser sam = data.user();
        timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "1:00", null);

        mvc.perform(post("/weeks/{day}/submit", WEDNESDAY).with(user(sam)).with(csrf()))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(content().string(containsString(">Submitted<")))
                .andExpect(content().string(containsString(">Reopen<")))
                .andExpect(content().string(not(containsString(">Submit week<"))));
    }

    @Test
    void editModeShowsTheEntryAsTyped() throws Exception {
        CurrentUser sam = data.user();
        long id = timesheets.addEntry(sam.asActor(), data.task(), WEDNESDAY, "23:10-24:00", null);

        mvc.perform(get("/").param("week", "2026-10-12").param("edit", String.valueOf(id)).with(user(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"11:10 PM\"")))
                .andExpect(content().string(containsString("value=\"12:00 AM\"")))
                .andExpect(content().string(containsString("value=\"0:50:00\"")));
    }
}
