package com.aryehgolob.timesheet.shared.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Which top-bar link a page belongs to, so the header can mark it as the current one. */
@ControllerAdvice
class NavSection {

    @ModelAttribute("navSection")
    String navSection(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/admin/period")) {
            return "period";
        }
        if (path.startsWith("/admin/")) {
            return "team";   // the grid, and anyone's week opened from it
        }
        return "week";
    }
}
