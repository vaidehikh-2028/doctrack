package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.ApprovalDecision;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * An approval decision on a forwarded request (Assignment 5, Approval state diagram):
 * PENDING -> APPROVED | REJECTED, and a recorded decision is never revised.
 */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private DocumentRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private User decidedBy;

    @Column(length = 500)
    private String comments;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant decidedAt;

    @Version
    private Long version;

    protected Approval() {}

    public Approval(DocumentRequest request, Instant now) {
        this.request = request;
        this.createdAt = now;
    }

    public void approve(User approver, String comments, Instant now) {
        decide(ApprovalDecision.APPROVED, approver, comments, now);
    }

    /** BR-005: a rejection needs a non-blank reason, which is stored as the comments. */
    public void reject(User approver, String reason, Instant now) {
        if (RequestValidator.isBlank(reason)) {
            throw new DomainException(ErrorCode.APR_REJECTION_REASON_REQUIRED);
        }
        decide(ApprovalDecision.REJECTED, approver, reason.trim(), now);
    }

    private void decide(ApprovalDecision d, User approver, String comments, Instant now) {
        if (decision != ApprovalDecision.PENDING) {
            throw new DomainException(ErrorCode.APR_INVALID_OR_ALREADY_DECIDED);
        }
        this.decision = d;
        this.decidedBy = approver;
        this.comments = comments == null ? null : comments.trim();
        this.decidedAt = now;
    }

    public Long getId() { return id; }
    public DocumentRequest getRequest() { return request; }
    public ApprovalDecision getDecision() { return decision; }
    public User getDecidedBy() { return decidedBy; }
    public String getComments() { return comments; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
