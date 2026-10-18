package com.aryehgolob.timesheet.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import com.aryehgolob.timesheet.identity.CurrentUser;
import com.aryehgolob.timesheet.shared.InvalidInputException;
import com.aryehgolob.timesheet.support.IntegrationTest;

class PeriodLockServiceTest extends IntegrationTest {

    static final LocalDate SEPT_30 = LocalDate.of(2026, 9, 30);

    @Autowired
    PeriodLockService lockService;

    @Autowired
    PeriodLocks locks;

    @AfterEach
    void releaseSharedLock() {
        data.unlock();
    }

    @Test
    void adminLocksAndUnlocksWithAnAuditTrail() {
        CurrentUser admin = data.admin();

        lockService.lockThrough(admin.asActor(), SEPT_30);
        assertThat(locks.current().isLocked(SEPT_30)).isTrue();
        assertThat(locks.current().isLocked(SEPT_30.plusDays(1))).isFalse();

        lockService.unlock(admin.asActor());
        assertThat(locks.current()).isEqualTo(PeriodLock.NONE);

        assertThat(data.audit("period_lock", 1))
                .extracting(r -> r.get("action")).endsWith("LOCK", "UNLOCK");
    }

    @Test
    void movingTheLineBackIsAllowedAndRecordsWhereItWas() {
        CurrentUser admin = data.admin();
        lockService.lockThrough(admin.asActor(), SEPT_30);

        lockService.lockThrough(admin.asActor(), SEPT_30.minusMonths(1));

        var last = data.audit("period_lock", 1).getLast();
        assertThat((String) last.get("before")).contains("2026-09-30");
        assertThat((String) last.get("after")).contains("2026-08-30");
    }

    @Test
    void regularUsersCannotLockEvenIfTheyReachTheService() {
        assertThatThrownBy(() -> lockService.lockThrough(data.user().asActor(), SEPT_30))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> lockService.unlock(data.user().asActor()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cannotCloseDaysThatHaveNotHappened() {
        assertThatThrownBy(() -> lockService.lockThrough(data.admin().asActor(), LocalDate.now().plusDays(2)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void lockPageIsAdminOnly() throws Exception {
        mvc.perform(get("/admin/period").with(user(data.user()))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/period").with(user(data.user())).with(csrf()).param("through", "2026-09-30"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/period").with(user(data.admin()))).andExpect(status().isOk());

        assertThat(locks.current()).isEqualTo(PeriodLock.NONE);
    }

    @Test
    void lockingAFutureDayFromThePageShowsAMessage() throws Exception {
        mvc.perform(post("/admin/period").with(user(data.admin())).with(csrf())
                        .param("through", LocalDate.now().plusDays(5).toString()))
                .andExpect(flash().attributeExists("error"));
    }
}
