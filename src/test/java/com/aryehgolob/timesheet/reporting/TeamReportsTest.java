package com.aryehgolob.timesheet.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.reporting.TeamOverview.Cell;
import com.aryehgolob.timesheet.reporting.TeamOverview.Row;
import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.tracking.TimesheetService;
import com.aryehgolob.timesheet.tracking.WeekSubmissionService;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/** Uses 2031 dates so nothing other tests log can leak into these totals. */
class TeamReportsTest extends IntegrationTest {

    static final WorkWeek WEEK_1 = new WorkWeek(LocalDate.of(2031, 3, 3));
    static final WorkWeek WEEK_2 = WEEK_1.next();
    static final ReportPeriod PERIOD = new ReportPeriod(WEEK_1, WEEK_2);

    @Autowired
    TeamReports reports;

    @Autowired
    TimesheetService timesheets;

    @Autowired
    WeekSubmissionService submissions;

    @Nested
    class Overview {

        @Test
        void showsEachPersonsWeeklyTotalAndWhetherItIsSubmitted() {
            CurrentUser sam = data.user();
            long task = data.task();
            timesheets.addEntry(sam.asActor(), task, WEEK_1.start(), "8:00", null);
            timesheets.addEntry(sam.asActor(), task, WEEK_1.start().plusDays(1), "0:30", null);
            timesheets.addEntry(sam.asActor(), task, WEEK_2.start(), "1:00", null);
            submissions.submit(sam.asActor(), sam.id(), WEEK_1);

            Row row = rowFor(sam);

            assertThat(row.cells()).containsExactly(new Cell(WEEK_1, 510, true), new Cell(WEEK_2, 60, false));
            assertThat(row.total()).isEqualTo(570);
        }

        @Test
        void activePeopleWithNoTimeStillShowUpSoMissingWeeksAreVisible() {
            CurrentUser idle = data.user();

            assertThat(rowFor(idle).cells()).allMatch(c -> c.minutes() == 0 && !c.submitted());
        }

        @Test
        void peopleWhoLeftAppearOnlyIfTheyLoggedTimeInThePeriod() {
            CurrentUser leftWithTime = data.user();
            timesheets.addEntry(leftWithTime.asActor(), data.task(), WEEK_2.start(), "2:00", null);
            data.deactivate(leftWithTime.id());
            CurrentUser leftWithout = data.user();
            data.deactivate(leftWithout.id());

            TeamOverview overview = reports.overview(data.admin().asActor(), PERIOD);

            assertThat(overview.rows()).extracting(Row::userId)
                    .contains(leftWithTime.id())
                    .doesNotContain(leftWithout.id());
        }

        @Test
        void isAdminOnlyEvenIfAUrlRuleWereForgotten() {
            assertThatThrownBy(() -> reports.overview(data.user().asActor(), PERIOD))
                    .isInstanceOf(AccessDeniedException.class);
        }

        private Row rowFor(CurrentUser user) {
            return reports.overview(data.admin().asActor(), PERIOD).rows().stream()
                    .filter(r -> r.userId() == user.id()).findFirst().orElseThrow();
        }
    }

    @Nested
    class Csv {

        @Test
        void hasOneRowPerPersonWeekAndTaskWithExactMinutes() throws IOException {
            CurrentUser sam = data.userNamed("Sam Csv");
            long dev = data.task("Website", "Acme", "Development");
            long meet = data.task("Website", "Acme", "Meetings");
            timesheets.addEntry(sam.asActor(), dev, WEEK_1.start(), "1:00", null);
            timesheets.addEntry(sam.asActor(), dev, WEEK_1.start().plusDays(2), "0:40", null);
            timesheets.addEntry(sam.asActor(), meet, WEEK_1.start(), "0:20", null);
            timesheets.addEntry(sam.asActor(), dev, WEEK_2.start(), "9:05-10:42", null);
            submissions.submit(sam.asActor(), sam.id(), WEEK_1);

            List<CSVRecord> rows = csvRowsFor(sam);

            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).toMap()).containsEntry("name", "Sam Csv")
                    .containsEntry("week_start", "2031-03-03").containsEntry("week_status", "submitted")
                    .containsEntry("client", "Acme").containsEntry("project", "Website")
                    .containsEntry("task", "Development").containsEntry("billable", "yes")
                    .containsEntry("minutes", "100").containsEntry("hours", "1.67");
            assertThat(rows.get(1).toMap()).containsEntry("task", "Meetings").containsEntry("minutes", "20")
                    .containsEntry("hours", "0.33");
            assertThat(rows.get(2).toMap()).containsEntry("week_start", "2031-03-10")
                    .containsEntry("week_status", "draft").containsEntry("minutes", "97");
        }

        @Test
        void quotesCommasQuotesAndNewlinesSoColumnsNeverShift() throws IOException {
            CurrentUser sam = data.userNamed("O'Brien, \"Sam\"");
            long task = data.task("Data, \"phase 2\"", "Globex\nInc", "Analysis");
            timesheets.addEntry(sam.asActor(), task, WEEK_1.start(), "1:00", null);

            CSVRecord row = csvRowsFor(sam).getFirst();

            assertThat(row.size()).isEqualTo(TeamReports.CSV_COLUMNS.length);
            assertThat(row.get("name")).isEqualTo("O'Brien, \"Sam\"");
            assertThat(row.get("project")).isEqualTo("Data, \"phase 2\"");
            assertThat(row.get("client")).isEqualTo("Globex\nInc");
        }

        @Test
        void neutralisesSpreadsheetFormulas() throws IOException {
            CurrentUser sam = data.userNamed("=HYPERLINK(\"http://evil\",\"x\")");
            long task = data.task("+cmd", "@client", "-task");
            timesheets.addEntry(sam.asActor(), task, WEEK_1.start(), "1:00", null);

            CSVRecord row = csvRowsFor(sam).getFirst();

            assertThat(row.get("name")).startsWith("'=");
            assertThat(row.get("project")).isEqualTo("'+cmd");
            assertThat(row.get("client")).isEqualTo("'@client");
            assertThat(row.get("task")).isEqualTo("'-task");
        }

        private List<CSVRecord> csvRowsFor(CurrentUser user) throws IOException {
            StringBuilder out = new StringBuilder();
            reports.writeCsv(data.admin().asActor(), PERIOD, out);
            var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                    .parse(new StringReader(out.toString()));
            assertThat(parser.getHeaderNames()).containsExactly(TeamReports.CSV_COLUMNS);
            return parser.getRecords().stream().filter(r -> r.get("email").equals(user.email())).toList();
        }
    }

    @Nested
    class Pages {

        @Test
        void teamPagesAndExportAreAdminOnly() throws Exception {
            CurrentUser sam = data.user();
            mvc.perform(get("/admin/weeks").with(user(sam))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/weeks/export.csv").param("from", "2031-03-03").param("to", "2031-03-16")
                    .with(user(sam))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/users/{id}/weeks", data.user().id()).with(user(sam)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void adminSeesTheOverviewAndDownloadsAFile() throws Exception {
            CurrentUser admin = data.admin();
            mvc.perform(get("/admin/weeks").param("from", "2031-03-03").param("to", "2031-03-16").with(user(admin)))
                    .andExpect(status().isOk());

            mvc.perform(get("/admin/weeks/export.csv").param("from", "2031-03-03").param("to", "2031-03-16")
                            .with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")))
                    .andExpect(header().string("Content-Disposition",
                            Matchers.containsString("timesheet-2031-03-03-to-2031-03-16.csv")));
        }

        @Test
        void aBackwardsRangeShowsAMessageInsteadOfFailing() throws Exception {
            mvc.perform(get("/admin/weeks").param("from", "2031-03-16").param("to", "2031-03-03")
                            .with(user(data.admin())))
                    .andExpect(status().isOk());
        }

        @Test
        void adminOpensSomeonesWeekReadOnlyAndCanSendItBack() throws Exception {
            CurrentUser admin = data.admin();
            CurrentUser sam = data.userNamed("Sam Reviewed");
            timesheets.addEntry(sam.asActor(), data.task(), WEEK_1.start(), "1:00", "visible note");
            submissions.submit(sam.asActor(), sam.id(), WEEK_1);

            mvc.perform(get("/admin/users/{id}/weeks", sam.id()).param("week", "2031-03-03").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(Matchers.allOf(
                                    Matchers.containsString("Sam Reviewed"),
                                    Matchers.containsString("visible note"),
                                    Matchers.containsString(">Reopen<"),
                                    Matchers.not(Matchers.containsString("id=\"quick-add\"")),
                                    Matchers.not(Matchers.containsString(">Edit<")))));

            mvc.perform(post("/admin/users/{id}/weeks/{day}/reopen", sam.id(), "2031-03-03")
                            .with(user(admin)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/users/" + sam.id() + "/weeks?week=2031-03-03"));
            assertThat(data.audit("week", sam.id() + ":2031-03-03"))
                    .extracting(r -> r.get("action")).containsExactly("SUBMIT", "REOPEN");
        }

        @Test
        void unknownPersonIsNotFound() throws Exception {
            mvc.perform(get("/admin/users/{id}/weeks", Long.MAX_VALUE).with(user(data.admin())))
                    .andExpect(status().isNotFound());
        }
    }
}
