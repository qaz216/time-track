package com.aryehgolob.timesheet.timesheet.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.aryehgolob.timesheet.identity.CurrentUser;

@Controller
class WeekController {

    @GetMapping("/")
    String week(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("user", user);
        return "week";
    }
}
