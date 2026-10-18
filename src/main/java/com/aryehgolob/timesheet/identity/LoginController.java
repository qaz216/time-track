package com.aryehgolob.timesheet.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

    /** Only a demo install (the dev profile, which seeds the demo accounts) advertises its login. */
    private final boolean demo;

    LoginController(@Value("${timesheet.seed-demo-data:false}") boolean demo) {
        this.demo = demo;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("demo", demo);
        return "login";
    }
}
