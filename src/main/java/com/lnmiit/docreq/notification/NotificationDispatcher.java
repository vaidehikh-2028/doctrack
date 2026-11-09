package com.lnmiit.docreq.notification;

import com.lnmiit.docreq.domain.Enums.DeliveryStatus;
import com.lnmiit.docreq.model.Notification;
import com.lnmiit.docreq.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Delivers PENDING notifications and retries FAILED ones with exponential backoff,
 * up to {@code app.notifications.max-attempts}. A gateway failure is recorded as
 * E-NTF-001 on the notification and never affects the request itself.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final NotificationGateway gateway;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final int batchSize;

    public NotificationDispatcher(NotificationRepository notifications,
                                  NotificationGateway gateway,
                                  @Value("${app.notifications.max-attempts:5}") int maxAttempts,
                                  @Value("${app.notifications.base-backoff:PT30S}") Duration baseBackoff,
                                  @Value("${app.notifications.batch-size:50}") int batchSize) {
        this.notifications = notifications;
        this.gateway = gateway;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.batchSize = batchSize;
    }

    /** @return number of notifications delivered in this run */
    @Transactional
    public int dispatchDue(Instant now) {
        List<Notification> due = notifications.findDue(DeliveryStatus.PENDING, DeliveryStatus.FAILED,
                maxAttempts, now, PageRequest.of(0, batchSize));
        int delivered = 0;
        for (Notification n : due) {
            try {
                gateway.send(n.getRecipient().getEmail(), subjectFor(n), n.getMessage());
                n.markDelivered();
                delivered++;
            } catch (NotificationGateway.DeliveryException | RuntimeException e) {
                n.markFailed("E-NTF-001: " + e.getMessage(), now, baseBackoff);
                log.warn("E-NTF-001 notification {} failed (attempt {}): {}", n.getId(), n.getAttempts(), e.getMessage());
            }
        }
        return delivered;
    }

    private static String subjectFor(Notification n) {
        String ref = n.getRequestNumber() == null ? "" : " - " + n.getRequestNumber();
        return switch (n.getType()) {
            case SUBMISSION_CONFIRMATION -> "Request received" + ref;
            case STATUS_CHANGE -> "Request status updated" + ref;
            case INCOMPLETE_REQUEST -> "Action needed: request incomplete" + ref;
            case APPROVAL_DECISION -> "Approval decision" + ref;
            case DOCUMENT_READY -> "Your document is ready" + ref;
            case SLA_ESCALATION -> "Overdue request" + ref;
        };
    }
}
