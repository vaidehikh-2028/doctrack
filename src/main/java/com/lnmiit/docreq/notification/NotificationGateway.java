package com.lnmiit.docreq.notification;

/**
 * External email/SMS gateway (Assignment 7, INotificationGateway).
 * Implementations throw {@link DeliveryException} when the message is refused.
 */
public interface NotificationGateway {

    void send(String toEmail, String subject, String body) throws DeliveryException;

    class DeliveryException extends Exception {
        public DeliveryException(String message) { super(message); }
        public DeliveryException(String message, Throwable cause) { super(message, cause); }
    }
}
