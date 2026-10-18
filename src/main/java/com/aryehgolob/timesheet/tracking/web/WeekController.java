package com.aryehgolob.timesheet.tracking.web;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aryehgolob.timesheet.catalog.Catalog;
import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.tracking.WeekQueries;
import com.aryehgolob.timesheet.tracking.WeekView;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

@Controller
class WeekController {

    private final WeekQueries weeks;
    private final Catalog catalog;
    private final Clock clock;

    WeekController(WeekQueries weeks, Catalog catalog, Clock clock) {
        this.weeks = weeks;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * @param week any date in the week to show; defaults to this week
     * @param edit id of the entry to show as an edit form
     */
    @GetMapping("/")
    String week(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate week,
            @RequestParam(required = false) Long edit,
            Model model) {
        LocalDate today = LocalDate.now(clock);
        WeekView view = weeks.week(user.asActor(), user.id(), WorkWeek.containing(week == null ? today : week));

        model.addAttribute("view", view);
        model.addAttribute("today", today);
        model.addAttribute("tasks", catalog.activeTasks());
        model.addAttribute("editId", edit);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", EntryForm.blank(defaultDay(view, today)));
        }
        return "week";
    }

    /** Today if you can log time today, else the first day you can. */
    private static LocalDate defaultDay(WeekView view, LocalDate today) {
        if (view.canEdit(today)) {
            return today;
        }
        return view.days().stream().filter(view::canEdit).findFirst().orElse(view.week().start());
    }
}
