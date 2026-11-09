package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The central transactional object. Its status can only change through
 * {@link #transitionTo}, which enforces the state machine in {@link RequestStatus}
 * and appends an entry to the append-only status history (FR-022).
 *
 * <p>{@code @Version} gives optimistic locking: if two staff members act on the
 * same request at once, the second commit fails instead of silently overwriting
 * the first (TC-14).
 */
@Entity
@Table(name = "document_requests", indexes = {
        @Index(name = "idx_request_status", columnList = "status"),
        @Index(name = "idx_request_student", columnList = "student_id")
})
public class DocumentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier REQ-YYYY-NNNNNN (FR-009). */
    @Column(unique = true, length = 20)
    private String requestNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    private DocumentType documentType;

    @Column(nullable = false, length = 250)
    private String purpose;

    @ElementCollection
    @CollectionTable(name = "request_field_values", joinColumns = @JoinColumn(name = "request_id"))
    @MapKeyColumn(name = "field_name", length = 60)
    @Column(name = "field_value", length = 500)
    private Map<String, String> fieldValues = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(length = 500)
    private String missingInfo;

    @Column(nullable = false)
    private Instant submittedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    /** Set once the SLA job has escalated this request, so it is escalated only once per stage. */
    private Instant escalatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    private List<StatusHistory> history = new ArrayList<>();

    @Version
    private Long version;

    protected DocumentRequest() {}

    /** Creates a request in state SUBMITTED with its first history entry (UC-005 step 8). */
    public static DocumentRequest submit(User student, DocumentType type, String purpose,
                                         Map<String, String> fieldValues, Instant now) {
        DocumentRequest r = new DocumentRequest();
        r.student = student;
        r.documentType = type;
        r.purpose = purpose.trim();
        r.fieldValues.putAll(fieldValues);
        r.status = RequestStatus.SUBMITTED;
        r.submittedAt = now;
        r.updatedAt = now;
        r.history.add(new StatusHistory(r, RequestStatus.SUBMITTED, student, "Request submitted", now));
        return r;
    }

    /**
     * The only way to change status. Refuses any transition the state diagram does
     * not allow (e.g. Rejected -> Processing, BR-006) without touching the history.
     */
    public void transitionTo(RequestStatus next, User actor, String remarks, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new DomainException(ErrorCode.VER_INVALID_REQUEST_STATE,
                    "Request " + requestNumber + " is " + status.label()
                            + " and cannot move to " + next.label() + ".");
        }
        status = next;
        updatedAt = now;
        escalatedAt = null; // a new stage gets a fresh SLA window
        if (next == RequestStatus.COMPLETED) {
            completedAt = now;
        }
        history.add(new StatusHistory(this, next, actor, remarks, now));
    }

    /** Assigns REQ-YYYY-NNNNNN from the database id. Called once, right after the first save. */
    public void assignRequestNumber() {
        if (requestNumber == null && id != null) {
            int year = submittedAt.atZone(ZoneOffset.UTC).getYear();
            requestNumber = String.format("REQ-%d-%06d", year, id);
        }
    }

    public void updateDetails(String purpose, Map<String, String> fieldValues) {
        this.purpose = purpose.trim();
        this.fieldValues.clear();
        this.fieldValues.putAll(fieldValues);
    }

    public boolean isOwnedBy(Long userId) { return student.getId().equals(userId); }

    public void markEscalated(Instant now) { this.escalatedAt = now; }

    public Long getId() { return id; }
    public String getRequestNumber() { return requestNumber; }
    public User getStudent() { return student; }
    public DocumentType getDocumentType() { return documentType; }
    public String getPurpose() { return purpose; }
    public Map<String, String> getFieldValues() { return Collections.unmodifiableMap(fieldValues); }
    public RequestStatus getStatus() { return status; }
    public String getMissingInfo() { return missingInfo; }
    public void setMissingInfo(String missingInfo) { this.missingInfo = missingInfo; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public List<StatusHistory> getHistory() { return Collections.unmodifiableList(history); }
    public Long getVersion() { return version; }
}
