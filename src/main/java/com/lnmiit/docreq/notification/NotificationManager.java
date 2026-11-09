package com.lnmiit.docreq.notification;

import com.lnmiit.docreq.domain.Enums.AccountStatus;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.model.Notification;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.NotificationRepository;
import com.lnmiit.docreq.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Creates notification records inside the caller's transaction (outbox pattern).
 * Nothing is sent here; {@link NotificationDispatcher} delivers them afterwards.
 */
@Component
public class NotificationManager {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public NotificationManager(NotificationRepository notifications, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    public Notification notify(User recipient, NotificationType type, String message, String requestNumber) {
        return notifications.save(new Notification(recipient, type, message, requestNumber, clock.instant()));
    }

    /** Notifies every active user with the given role (e.g. all staff after an approval). */
    public void notifyRole(UserRole role, NotificationType type, String message, String requestNumber) {
        for (User u : users.findByRoleAndAccountStatus(role, AccountStatus.ACTIVE)) {
            notify(u, type, message, requestNumber);
        }
    }
}
