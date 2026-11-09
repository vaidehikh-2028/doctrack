package com.lnmiit.docreq.model;

import com.lnmiit.docreq.domain.RequestValidator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * A document the institute offers (Bonafide, Character Certificate, ...).
 * New document types are data, not code (NFR-012): the admin configures their
 * required fields, whether an attachment is needed and whether approval is needed.
 */
@Entity
@Table(name = "document_types")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_type_fields", joinColumns = @JoinColumn(name = "document_type_id"))
    @OrderColumn(name = "field_order")
    @Column(name = "field_name", nullable = false, length = 60)
    private List<String> requiredFields = new ArrayList<>();

    @Column(nullable = false)
    private boolean attachmentRequired;

    @Column(nullable = false)
    private boolean approvalRequired;

    @Column(nullable = false)
    private int processingDays;

    @Column(nullable = false)
    private boolean active = true;

    protected DocumentType() {}

    public DocumentType(String code, String name, String description, List<String> requiredFields,
                        boolean attachmentRequired, boolean approvalRequired, int processingDays) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.requiredFields = new ArrayList<>(requiredFields);
        this.attachmentRequired = attachmentRequired;
        this.approvalRequired = approvalRequired;
        this.processingDays = processingDays;
    }

    public RequestValidator.TypeSpec toSpec() {
        return new RequestValidator.TypeSpec(active, List.copyOf(requiredFields), attachmentRequired);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getRequiredFields() { return requiredFields; }
    public boolean isAttachmentRequired() { return attachmentRequired; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public int getProcessingDays() { return processingDays; }
    public boolean isActive() { return active; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setRequiredFields(List<String> fields) {
        this.requiredFields.clear();
        this.requiredFields.addAll(fields);
    }
    public void setAttachmentRequired(boolean attachmentRequired) { this.attachmentRequired = attachmentRequired; }
    public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }
    public void setProcessingDays(int processingDays) { this.processingDays = processingDays; }
    public void setActive(boolean active) { this.active = active; }
}
