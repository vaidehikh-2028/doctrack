package com.lnmiit.docreq.workflow;

import com.lnmiit.docreq.domain.Enums.DeliveryStatus;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.model.Notification;
import com.lnmiit.docreq.notification.NotificationDispatcher;
import com.lnmiit.docreq.notification.NotificationGateway;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * TC-09 (gateway failure part) and E-NTF-001: a failing email gateway never loses the
 * request; the notification is stored as FAILED and retried with backoff.
 */
class NotificationDispatchTest extends IntegrationTestBase {

    @MockBean NotificationGateway gateway;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired NotificationRepository notifications;
    @Autowired DocumentRequestRepository requests;
    @Autowired Clock clock;

    private Notification confirmationOf(String number) {
        return notifications.findByRequestNumberAndType(number, NotificationType.SUBMISSION_CONFIRMATION).get(0);
    }

    @Test
    @DisplayName("Gateway down: request saved, notification FAILED, retried after backoff, then DELIVERED")
    void failedDeliveryIsRetried() throws Exception {
        reset(gateway);
        doThrow(new NotificationGateway.DeliveryException("SMTP unavailable"))
                .when(gateway).send(anyString(), anyString(), anyString());

        String number = submitValid("student1", BONAFIDE);
        assertThat(requests.findByRequestNumber(number)).isPresent();

        Instant t0 = clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        dispatcher.dispatchDue(t0);
        Notification n = confirmationOf(number);
        assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getLastError()).startsWith("E-NTF-001");
        assertThat(n.getNextAttemptAt()).isEqualTo(t0.plus(Duration.ofSeconds(30)));

        // not due yet: no new attempt
        dispatcher.dispatchDue(t0.plusSeconds(10));
        assertThat(confirmationOf(number).getAttempts()).isEqualTo(1);

        // gateway back up
        reset(gateway);
        doNothing().when(gateway).send(anyString(), anyString(), anyString());
        dispatcher.dispatchDue(t0.plusSeconds(31));
        assertThat(confirmationOf(number).getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(confirmationOf(number).getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Delivery stops after max attempts (3 in tests) with exponential backoff")
    void stopsAfterMaxAttempts() throws Exception {
        reset(gateway);
        doThrow(new NotificationGateway.DeliveryException("bounced"))
                .when(gateway).send(anyString(), anyString(), anyString());

        String number = submitValid("student2", BONAFIDE);
        Instant t = clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        dispatcher.dispatchDue(t);                       // attempt 1, next +30s
        dispatcher.dispatchDue(t.plusSeconds(31));       // attempt 2, next +60s
        assertThat(confirmationOf(number).getNextAttemptAt()).isEqualTo(t.plusSeconds(31 + 60));
        dispatcher.dispatchDue(t.plusSeconds(200));      // attempt 3
        dispatcher.dispatchDue(t.plusSeconds(10_000));   // max reached: no more attempts
        Notification n = confirmationOf(number);
        assertThat(n.getAttempts()).isEqualTo(3);
        assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }
}
