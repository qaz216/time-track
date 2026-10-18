package com.aryehgolob.timesheet.tracking.web;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aryehgolob.timesheet.catalog.Catalog;
import com.aryehgolob.timesheet.catalog.TaskOption;
import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.tracking.WeekQueries;
import com.aryehgolob.timesheet.tracking.WeekSubmissionService;
import com.aryehgolob.timesheet.tracking.WeekView;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * The week page: your own at "/", anyone's (read-only) for admins under
 * /admin/users/{id}/weeks. Same template, same view model; EditPolicy decides
 * what the viewer may do, so an admin simply gets no edit controls.
 */
@Controller
class WeekController {

    private final WeekQueries weeks;
    private final WeekSubmissionService submissions;
    private final Catalog catalog;
    private final Clock clock;

    WeekController(WeekQueries weeks, WeekSubmissionService submissions, Catalog catalog, Clock clock) {
        this.weeks = weeks;
        this.submissions = submissions;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * @param week any date in the week to show; defaults to this week
     * @param edit id of the entry to show as an edit form
     */
    @GetMapping("/")
    String myWeek(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate week,
            @RequestParam(required = false) Long edit,
            Model model) {
        WeekView view = show(user, user.id(), week, model);
        model.addAttribute("ownWeek", true);
        model.addAttribute("weekUrl", "/");
        model.addAttribute("reopenUrl", "/weeks/" + view.week().start() + "/reopen");
        model.addAttribute("editId", edit);
        // grouped by project so options are task names, and type-to-select works on them
        model.addAttribute("taskGroups", catalog.activeTasks().stream()
                .collect(Collectors.groupingBy(TaskOption::projectCode, LinkedHashMap::new, Collectors.toList())));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", EntryForm.blank(defaultDay(view, LocalDate.now(clock))));
        }
        return "week";
    }

    @GetMapping("/admin/users/{userId}/weeks")
    String someonesWeek(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate week,
            Model model) {
        if (userId == user.id()) {
            return "redirect:/" + (week == null ? "" : "?week=" + week);
        }
        WeekView view = show(user, userId, week, model);
        model.addAttribute("ownWeek", false);
        model.addAttribute("weekUrl", "/admin/users/" + userId + "/weeks");
        model.addAttribute("reopenUrl", "/admin/users/" + userId + "/weeks/" + view.week().start() + "/reopen");
        return "week";
    }

    /** An admin sends a submitted week back to its owner. */
    @PostMapping("/admin/users/{userId}/weeks/{day}/reopen")
    String sendBack(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long userId,
            @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate day) {
        WorkWeek week = WorkWeek.containing(day);
        submissions.reopen(user.asActor(), userId, week);
        return "redirect:/admin/users/" + userId + "/weeks?week=" + week.start();
    }

    private WeekView show(CurrentUser viewer, long ownerId, LocalDate anyDay, Model model) {
        LocalDate today = LocalDate.now(clock);
        WeekView view = weeks.week(viewer.asActor(), ownerId, WorkWeek.containing(anyDay == null ? today : anyDay));
        model.addAttribute("view", view);
        model.addAttribute("today", today);
        return view;
    }

    /** Today if you can log time today, else the first day you can. */
    private static LocalDate defaultDay(WeekView view, LocalDate today) {
        if (view.canEdit(today)) {
            return today;
        }
        return view.days().stream().filter(view::canEdit).findFirst().orElse(view.week().start());
    }
}
