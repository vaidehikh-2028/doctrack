package com.lnmiit.docreq.jobs;

import com.lnmiit.docreq.notification.NotificationDispatcher;
import com.lnmiit.docreq.service.SlaEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Background jobs. Each job delegates to a service method that takes the current
 * time as a parameter, which keeps the logic deterministic and easy to test.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final NotificationDispatcher dispatcher;
    private final SlaEscalationService slaEscalation;
    private final Clock clock;

    public ScheduledJobs(NotificationDispatcher dispatcher, SlaEscalationService slaEscalation, Clock clock) {
        this.dispatcher = dispatcher;
        this.slaEscalation = slaEscalation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval:PT15S}")
    public void dispatchNotifications() {
        int sent = dispatcher.dispatchDue(clock.instant());
        if (sent > 0) {
            log.debug("Delivered {} notifications", sent);
        }
    }

    @Scheduled(cron = "${app.sla.cron:0 0 * * * *}")
    public void escalateOverdueRequests() {
        int escalated = slaEscalation.escalateOverdue(clock.instant());
        if (escalated > 0) {
            log.info("Escalated {} overdue requests", escalated);
        }
    }
}
