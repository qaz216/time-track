package com.aryehgolob.timesheet.reporting.web;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.period.PeriodLocks;
import com.aryehgolob.timesheet.reporting.ReportPeriod;
import com.aryehgolob.timesheet.reporting.TeamReports;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

@Controller
@RequestMapping("/admin/weeks")
class TeamWeeksController {

    private final TeamReports reports;
    private final PeriodLocks locks;
    private final Clock clock;

    TeamWeeksController(TeamReports reports, PeriodLocks locks, Clock clock) {
        this.reports = reports;
        this.locks = locks;
        this.clock = clock;
    }

    /** Defaults to the last four weeks, this one included. */
    @GetMapping
    String overview(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            Model model) {
        ReportPeriod period;
        try {
            period = period(from, to);
        } catch (InvalidInputException e) {
            model.addAttribute("error", e.getMessage());
            period = period(null, null);
        }
        model.addAttribute("overview", reports.overview(user.asActor(), period));
        model.addAttribute("lock", locks.current());
        model.addAttribute("thisWeek", WorkWeek.containing(LocalDate.now(clock)));
        return "admin/weeks";
    }

    @GetMapping("/export.csv")
    void csv(@AuthenticationPrincipal CurrentUser user,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {
        ReportPeriod period = ReportPeriod.between(from, to);
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("timesheet-" + period.start() + "-to-" + period.end() + ".csv").build().toString());
        Writer out = response.getWriter();
        reports.writeCsv(user.asActor(), period, out);
        out.flush();
    }

    private ReportPeriod period(LocalDate from, LocalDate to) {
        WorkWeek thisWeek = WorkWeek.containing(LocalDate.now(clock));
        LocalDate start = from != null ? from : thisWeek.start().minusWeeks(3);
        LocalDate end = to != null ? to : (from != null ? start.plusWeeks(3) : thisWeek.end());
        return ReportPeriod.between(start, end);
    }
}
