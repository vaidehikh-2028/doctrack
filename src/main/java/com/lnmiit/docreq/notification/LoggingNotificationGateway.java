package com.lnmiit.docreq.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development gateway that writes notifications to the log instead of sending them.
 * Swap in an AWS SES or SMTP implementation for production.
 */
@Component
public class LoggingNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationGateway.class);

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("NOTIFY to={} subject=\"{}\" body=\"{}\"", toEmail, subject, body);
    }
}
