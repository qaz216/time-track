package com.aryehgolob.timesheet.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;

import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.support.TestData;

class SecurityTest extends IntegrationTest {

    @Nested
    class SigningIn {

        @Test
        void withTheRightPasswordLandsOnTheWeek() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.email(), TestData.PASSWORD))
                    .andExpect(authenticated().withUsername(sam.email()))
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        void emailIsCaseInsensitive() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.email().toUpperCase(), TestData.PASSWORD))
                    .andExpect(authenticated());
        }

        @Test
        void withTheWrongPasswordIsRefused() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.email(), "wrong"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
            mvc.perform(login("nobody@test.example", "whatever"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        void deactivatedUserCannotSignIn() throws Exception {
            CurrentUser former = data.inactiveUser();

            mvc.perform(login(former.email(), TestData.PASSWORD))
                    .andExpect(unauthenticated());
        }

        private static FormLoginRequestBuilder login(String email, String password) {
            return formLogin("/login").userParameter("email").user(email).password(password);
        }
    }

    @Nested
    class Access {

        @Test
        void anonymousIsSentToLogin() throws Exception {
            mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
            mvc.perform(get("/admin/weeks")).andExpect(redirectedUrl("/login"));
        }

        @Test
        void loginPageAndStylesArePublic() throws Exception {
            mvc.perform(get("/login")).andExpect(status().isOk());
            mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        }

        @Test
        void signedInUserSeesTheirWeek() throws Exception {
            mvc.perform(get("/").with(user(data.user()))).andExpect(status().isOk());
        }

        @Test
        void regularUserIsForbiddenFromAdminPages() throws Exception {
            mvc.perform(get("/admin/weeks").with(user(data.user()))).andExpect(status().isForbidden());
            mvc.perform(get("/admin/anything").with(user(data.user()))).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Csrf {

        @Test
        void postWithoutTokenIsRejected() throws Exception {
            mvc.perform(post("/logout").with(user(data.user()))).andExpect(status().isForbidden());
        }

        @Test
        void postWithTokenIsAccepted() throws Exception {
            mvc.perform(post("/logout").with(user(data.user())).with(csrf()))
                    .andExpect(redirectedUrl("/login?logout"));
        }
    }
}
