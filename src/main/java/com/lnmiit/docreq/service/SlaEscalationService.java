package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.notification.NotificationManager;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * SLA reminders (Could-Have, Assignment 2 Section 21). A request waiting on staff or
 * an approver for longer than {@code app.sla.threshold} is escalated once per stage:
 * the responsible role and the administrators are notified.
 */
@Service
public class SlaEscalationService {

    static final Set<RequestStatus> WAITING_ON_STAFF =
            EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PROCESSING);
    static final Set<RequestStatus> WAITING_ON_APPROVER = EnumSet.of(RequestStatus.FORWARDED);

    private final DocumentRequestRepository requests;
    private final NotificationManager notifications;
    private final Duration threshold;

    public SlaEscalationService(DocumentRequestRepository requests, NotificationManager notifications,
                                @Value("${app.sla.threshold:PT48H}") Duration threshold) {
        this.requests = requests;
        this.notifications = notifications;
        this.threshold = threshold;
    }

    /** @return number of requests escalated */
    @Transactional
    public int escalateOverdue(Instant now) {
        Set<RequestStatus> open = EnumSet.copyOf(WAITING_ON_STAFF);
        open.addAll(WAITING_ON_APPROVER);
        List<DocumentRequest> overdue = requests.findOverdue(open, now.minus(threshold));
        for (DocumentRequest r : overdue) {
            String msg = "Request " + r.getRequestNumber() + " has been " + r.getStatus().label()
                    + " for more than " + threshold.toHours() + " hours.";
            UserRole responsible = WAITING_ON_APPROVER.contains(r.getStatus()) ? UserRole.APPROVER : UserRole.STAFF;
            notifications.notifyRole(responsible, NotificationType.SLA_ESCALATION, msg, r.getRequestNumber());
            notifications.notifyRole(UserRole.ADMIN, NotificationType.SLA_ESCALATION, msg, r.getRequestNumber());
            r.markEscalated(now);
        }
        return overdue.size();
    }
}
