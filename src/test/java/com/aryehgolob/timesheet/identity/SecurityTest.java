package com.aryehgolob.timesheet.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;

import com.aryehgolob.timesheet.support.IntegrationTest;
import com.aryehgolob.timesheet.support.TestData;

class SecurityTest extends IntegrationTest {

    @Nested
    class SigningIn {

        @Test
        void withUsernameAndTheRightPasswordLandsOnTheWeek() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.username(), TestData.PASSWORD))
                    .andExpect(authenticated().withUsername(sam.username()))
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        void usernameIsCaseInsensitive() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.username().toUpperCase(), TestData.PASSWORD))
                    .andExpect(authenticated().withUsername(sam.username()));
        }

        @Test
        void anEmailAddressWorksTooForPeopleWhoReachForIt() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.email().toUpperCase(), TestData.PASSWORD))
                    .andExpect(authenticated().withUsername(sam.username()));
        }

        @Test
        void withTheWrongPasswordIsRefused() throws Exception {
            CurrentUser sam = data.user();

            mvc.perform(login(sam.username(), "wrong"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        void unknownUsernameGetsTheSameAnswerAsAWrongPassword() throws Exception {
            mvc.perform(login("nobody", "whatever"))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        void deactivatedUserCannotSignIn() throws Exception {
            CurrentUser former = data.inactiveUser();

            mvc.perform(login(former.username(), TestData.PASSWORD))
                    .andExpect(unauthenticated());
        }

        private static FormLoginRequestBuilder login(String username, String password) {
            return formLogin("/login").user(username).password(password);
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
        void scriptsAndStylesAreLinkedByContentHashSoBrowsersNeverRunStaleCopies() throws Exception {
            String page = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

            Matcher css = Pattern.compile("/css/app-[0-9a-f]{32}\\.css").matcher(page);
            assertThat(css.find()).as("hashed stylesheet link").isTrue();
            assertThat(page).containsPattern("/js/time-fields-[0-9a-f]{32}\\.js");
            mvc.perform(get(css.group())).andExpect(status().isOk());
        }

        @Test
        void theSignInPageOnlyAdvertisesTheDemoLoginInADemoInstall() throws Exception {
            // tests run without the dev profile, like production: no password hint on the page
            mvc.perform(get("/login"))
                    .andExpect(content().string(containsString("name=\"username\"")))
                    .andExpect(content().string(not(containsString("demo-hint"))));
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
