package com.aryehgolob.timesheet.tracking.web;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.tracking.TimerService;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;

/**
 * Timer actions always land back on the week page. Refusals are shown as a
 * message rather than a 403 page here, because a timer can legitimately end up
 * somewhere it can't be logged (the week got submitted while it ran).
 */
@Controller
@RequestMapping("/timer")
class TimerController {

    private final TimerService timers;

    TimerController(TimerService timers) {
        this.timers = timers;
    }

    @PostMapping("/start")
    String start(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String note,
            RedirectAttributes redirect) {
        if (taskId == null) {
            redirect.addFlashAttribute("error", "Choose a task, then start the timer.");
            return "redirect:/";
        }
        try {
            timers.start(user.asActor(), taskId, note);
        } catch (InvalidInputException | NotAllowedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    /** @param stoppedAt only sent from the "forgot it running?" panel */
    @PostMapping("/stop")
    String stop(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime stoppedAt,
            RedirectAttributes redirect) {
        try {
            List<Long> created = timers.stop(user.asActor(), stoppedAt);
            if (created.isEmpty()) {
                redirect.addFlashAttribute("notice", "The timer ran for under a minute, so nothing was logged.");
            }
        } catch (InvalidInputException | NotAllowedException e) {
            redirect.addFlashAttribute("error", "Couldn't log the timer: " + e.getMessage()
                    + " It is still running; you can discard it.");
        }
        return "redirect:/";
    }

    @PostMapping("/discard")
    String discard(@AuthenticationPrincipal CurrentUser user) {
        timers.discard(user.asActor());
        return "redirect:/";
    }
}
