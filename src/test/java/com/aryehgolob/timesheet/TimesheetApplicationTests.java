package com.aryehgolob.timesheet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the whole app against real Postgres: Flyway runs, Hibernate validates the schema. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TimesheetApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void schemaMigratesAndMatchesEntities() {
        // context startup is the assertion
    }

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
