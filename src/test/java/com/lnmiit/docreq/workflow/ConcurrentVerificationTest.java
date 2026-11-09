package com.lnmiit.docreq.workflow;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.NotificationRepository;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.VerificationService;
import com.lnmiit.docreq.web.dto.Dtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-14 under real concurrency: two staff members verify the same request at the same
 * instant. Exactly one succeeds. The other waits on the row lock, then sees the new status
 * and is refused with E-VER-001 (or E-SYS-409 from the @Version backstop). There is never a
 * duplicate history entry or notification.
 */
class ConcurrentVerificationTest extends IntegrationTestBase {

    @Autowired VerificationService verification;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    private AuthUser auth(String username) {
        User u = users.findByUsernameIgnoreCase(username).orElseThrow();
        return new AuthUser(u.getId(), u.getUsername(), u.getName(), u.getRole());
    }

    @RepeatedTest(5)
    @DisplayName("TC-14 concurrent verification: exactly one wins")
    void onlyOneVerificationWins() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String staff : List.of("staff1", "staff2")) {
                AuthUser actor = auth(staff);
                Callable<String> task = () -> {
                    start.await();
                    try {
                        verification.verify(actor, number,
                                new Dtos.VerifyCommand(Dtos.VerifyOutcome.VERIFIED, null, "by " + staff));
                        return "OK";
                    } catch (DomainException e) {
                        return e.getErrorCode().code();
                    } catch (ConcurrencyFailureException e) {
                        return "E-SYS-409";
                    } catch (RuntimeException e) {
                        return "REFUSED:" + e.getClass().getSimpleName();
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }

            // With the row lock the loser normally waits, sees PROCESSING and gets E-VER-001;
            // whatever the database does, exactly one verification may succeed.
            assertThat(outcomes).hasSize(2).containsOnlyOnce("OK");
            String loser = outcomes.stream().filter(o -> !o.equals("OK")).findFirst().orElseThrow();
            System.out.println("TC-14 concurrent loser outcome: " + loser);
            assertThat(loser).isNotEqualTo("OK");
        } finally {
            pool.shutdownNow();
        }

        var d = detail("staff1", number);
        assertThat(d.get("status").asText()).isEqualTo(RequestStatus.PROCESSING.name());
        assertThat(d.get("history").findValuesAsText("status")).containsExactly("SUBMITTED", "VERIFIED", "PROCESSING");
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.STATUS_CHANGE)).hasSize(1);
    }
}
