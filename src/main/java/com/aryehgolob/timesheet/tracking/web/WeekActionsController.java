package com.aryehgolob.timesheet.tracking.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.tracking.WeekSubmissionService;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/** Submitting and reopening your own week. Admins reopen other people's from the team page. */
@Controller
@RequestMapping("/weeks/{day}")
class WeekActionsController {

    private final WeekSubmissionService submissions;

    WeekActionsController(WeekSubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping("/submit")
    String submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate day) {
        WorkWeek week = WorkWeek.containing(day);
        submissions.submit(user.asActor(), user.id(), week);
        return "redirect:/?week=" + week.start();
    }

    @PostMapping("/reopen")
    String reopen(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate day) {
        WorkWeek week = WorkWeek.containing(day);
        submissions.reopen(user.asActor(), user.id(), week);
        return "redirect:/?week=" + week.start();
    }
}
