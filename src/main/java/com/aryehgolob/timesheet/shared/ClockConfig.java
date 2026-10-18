package com.aryehgolob.timesheet.shared;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One company, one calendar: "today" and midnight are decided in the office's zone. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock(@Value("${timesheet.zone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
