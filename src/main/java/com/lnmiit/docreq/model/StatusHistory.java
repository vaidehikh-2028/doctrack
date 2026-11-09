package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.RequestStatus;
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

import java.time.Instant;

/** One immutable entry in a request's status log (FR-022, NFR-015). No setters by design. */
@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private DocumentRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    @Column(length = 500)
    private String remarks;

    protected StatusHistory() {}

    StatusHistory(DocumentRequest request, RequestStatus status, User changedBy, String remarks, Instant changedAt) {
        this.request = request;
        this.status = status;
        this.changedBy = changedBy;
        this.remarks = remarks;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public DocumentRequest getRequest() { return request; }
    public RequestStatus getStatus() { return status; }
    public User getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getRemarks() { return remarks; }
}
