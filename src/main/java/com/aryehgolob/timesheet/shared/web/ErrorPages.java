package com.aryehgolob.timesheet.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.aryehgolob.timesheet.tracking.EntryNotFoundException;
import com.aryehgolob.timesheet.tracking.domain.NotAllowedException;

/** Turns domain refusals into proper HTTP answers with a readable page. */
@ControllerAdvice
class ErrorPages {

    @ExceptionHandler(NotAllowedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String notAllowed(NotAllowedException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "problem";
    }

    @ExceptionHandler(EntryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(Model model) {
        model.addAttribute("message", "That entry doesn't exist. It may have been deleted.");
        return "problem";
    }
}
