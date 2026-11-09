package com.lnmiit.docreq.domain;

/**
 * Holder for the remaining enumerations of Assignment 6, Section 43.2.
 * Kept as nested types so the domain package stays small.
 */
public final class Enums {
    private Enums() {}

    public enum UserRole { STUDENT, STAFF, APPROVER, ADMIN }

    public enum AccountStatus { ACTIVE, INACTIVE }

    public enum ApprovalDecision { PENDING, APPROVED, REJECTED }

    public enum NotificationType {
        SUBMISSION_CONFIRMATION,
        STATUS_CHANGE,
        INCOMPLETE_REQUEST,
        APPROVAL_DECISION,
        DOCUMENT_READY,
        /** Added for the "SLA-based reminders" Could-Have requirement (Assignment 2, Section 21). */
        SLA_ESCALATION
    }

    public enum DeliveryStatus { PENDING, DELIVERED, FAILED }
}
