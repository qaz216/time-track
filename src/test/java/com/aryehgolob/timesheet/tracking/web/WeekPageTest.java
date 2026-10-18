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
                        .param("time", "9:05-10:42")
                        .param("note", "Kickoff"))
                .andExpect(redirectedUrl("/?week=2026-10-12"));

        mvc.perform(get("/").param("week", "2026-10-12").with(user(sam)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("09:05-10:42")))
                .andExpect(content().string(containsString("1:37")))
                .andExpect(content().string(containsString("Kickoff")));
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
    void badInputComesBackAsAMessageWithWhatWasTyped() throws Exception {
        CurrentUser sam = data.user();

        mvc.perform(post("/entries").with(user(sam)).with(csrf())
                        .param("taskId", String.valueOf(data.task()))
                        .param("date", WEDNESDAY.toString())
                        .param("time", "1.33"))
                .andExpect(redirectedUrl("/?week=2026-10-12"))
                .andExpect(flash().attribute("error", containsString("Did you mean 1:20?")));
        assertThat(data.entryCount(sam.id())).isZero();
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
                        .param("version", "0").param("time", "8:00").param("week", "2026-10-12"))
                .andExpect(status().isForbidden());

        assertThat(data.entry(samsEntry).get("minutes")).isEqualTo(60);
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
                .andExpect(content().string(containsString("value=\"23:10-24:00\"")));
    }
}
