package com.aryehgolob.timesheet.period.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.period.PeriodLockService;
import com.aryehgolob.timesheet.period.PeriodLocks;
import com.aryehgolob.timesheet.shared.InvalidInputException;

@Controller
@RequestMapping("/admin/period")
class PeriodLockController {

    private final PeriodLocks locks;
    private final PeriodLockService lockService;
    private final Clock clock;

    PeriodLockController(PeriodLocks locks, PeriodLockService lockService, Clock clock) {
        this.locks = locks;
        this.lockService = lockService;
        this.clock = clock;
    }

    @GetMapping
    String show(Model model) {
        model.addAttribute("lock", locks.current());
        // finance usually closes last month
        model.addAttribute("suggested", LocalDate.now(clock).minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()));
        model.addAttribute("today", LocalDate.now(clock));
        return "admin/period";
    }

    @PostMapping
    String lock(@AuthenticationPrincipal CurrentUser user,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate through,
            RedirectAttributes redirect) {
        try {
            lockService.lockThrough(user.asActor(), through);
        } catch (InvalidInputException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/period";
    }

    @PostMapping("/unlock")
    String unlock(@AuthenticationPrincipal CurrentUser user) {
        lockService.unlock(user.asActor());
        return "redirect:/admin/period";
    }
}
