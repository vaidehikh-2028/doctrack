package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.Enums.DeliveryStatus;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * A notification record. It is written in the same transaction as the status change
 * (transactional outbox) and delivered later by {@code NotificationDispatcher}, so a
 * failing email gateway can never roll back or lose a saved request (NFR-008, E-NTF-001).
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 20)
    private String requestNumber;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(length = 300)
    private String lastError;

    protected Notification() {}

    public Notification(User recipient, NotificationType type, String message, String requestNumber, Instant now) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.requestNumber = requestNumber;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public void markDelivered() {
        attempts++;
        deliveryStatus = DeliveryStatus.DELIVERED;
        lastError = null;
    }

    /** Exponential backoff: base, 2x base, 4x base, ... */
    public void markFailed(String error, Instant now, Duration baseBackoff) {
        attempts++;
        deliveryStatus = DeliveryStatus.FAILED;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 300));
        nextAttemptAt = now.plus(baseBackoff.multipliedBy(1L << Math.min(attempts - 1, 10)));
    }

    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public String getRequestNumber() { return requestNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
}
