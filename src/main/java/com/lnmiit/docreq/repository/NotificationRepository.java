package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.domain.Enums.DeliveryStatus;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRequestNumberOrderByCreatedAtAsc(String requestNumber);

    List<Notification> findByRequestNumberAndType(String requestNumber, NotificationType type);

    /** Notifications that are new, or failed and due for another attempt. */
    @Query("select n from Notification n where (n.deliveryStatus = :pending "
            + "or (n.deliveryStatus = :failed and n.attempts < :maxAttempts)) "
            + "and n.nextAttemptAt <= :now order by n.createdAt asc")
    List<Notification> findDue(@Param("pending") DeliveryStatus pending,
                               @Param("failed") DeliveryStatus failed,
                               @Param("maxAttempts") int maxAttempts,
                               @Param("now") Instant now,
                               Pageable page);
}
