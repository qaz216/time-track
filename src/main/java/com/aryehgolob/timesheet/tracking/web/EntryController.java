package com.aryehgolob.timesheet.tracking.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.StaleEntryException;
import com.aryehgolob.timesheet.tracking.TimesheetService;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;
import com.aryehgolob.timesheet.tracking.domain.WorkWeek;

/**
 * Plain HTML forms, post-redirect-get. Bad input comes back as a message on the
 * week page; rule violations (someone else's entry, locked period) are 403s
 * because the UI never offers them.
 */
@Controller
@RequestMapping("/entries")
class EntryController {

    private final TimesheetService timesheets;

    EntryController(TimesheetService timesheets) {
        this.timesheets = timesheets;
    }

    /**
     * Every field is optional at the HTTP level so a half-filled bar comes back
     * with a message, not a 400. A day that turns out to be locked or submitted
     * (the date box allows any day of the week) is a message too.
     */
    @PostMapping
    String add(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") String time,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate week,
            RedirectAttributes redirect) {
        LocalDate day = date != null ? date : (week != null ? week : LocalDate.now());
        String problem = taskId == null ? "Pick a project." : date == null ? "Pick a day." : null;
        if (problem == null) {
            try {
                timesheets.addEntry(user.asActor(), taskId, date, time, note);
                // keep the project and day, clear the rest: the next entry is a few keystrokes away
                redirect.addFlashAttribute("form", new EntryForm(taskId, date, "", ""));
                return toWeek(day, null);
            } catch (InvalidInputException | NotAllowedException e) {
                problem = e.getMessage();
            }
        }
        redirect.addFlashAttribute("error", problem);
        redirect.addFlashAttribute("form", new EntryForm(taskId, date, time, note));
        return toWeek(day, null);
    }

    /** @param week only used to send the user back where they were */
    @PostMapping("/{id}")
    String update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long id,
            @RequestParam int version,
            @RequestParam String time,
            @RequestParam(required = false) String note,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate week,
            RedirectAttributes redirect) {
        try {
            timesheets.updateEntry(user.asActor(), id, version, time, note);
            return toWeek(week, null);
        } catch (StaleEntryException e) {
            // show the latest saved values, not what was typed over an old version
            redirect.addFlashAttribute("editError", e.getMessage());
            return toWeek(week, id);
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("editError", e.getMessage());
            redirect.addFlashAttribute("editTime", time);
            redirect.addFlashAttribute("editNote", note);
            return toWeek(week, id);
        }
    }

    @PostMapping("/{id}/delete")
    String delete(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long id,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate week) {
        timesheets.deleteEntry(user.asActor(), id);
        return toWeek(week, null);
    }

    private static String toWeek(LocalDate anyDay, Long editId) {
        String url = "redirect:/?week=" + WorkWeek.containing(anyDay).start();
        return editId == null ? url : url + "&edit=" + editId;
    }
}
