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

    @PostMapping
    String add(@AuthenticationPrincipal CurrentUser user,
            @RequestParam long taskId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam String time,
            @RequestParam(required = false) String note,
            RedirectAttributes redirect) {
        try {
            timesheets.addEntry(user.asActor(), taskId, date, time, note);
            // keep the task and day so the next entry is one keystroke away
            redirect.addFlashAttribute("form", new EntryForm(taskId, date, "", ""));
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("form", new EntryForm(taskId, date, time, note));
        }
        return toWeek(date, null);
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
