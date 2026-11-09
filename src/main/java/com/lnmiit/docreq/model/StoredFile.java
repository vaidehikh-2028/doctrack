package com.lnmiit.docreq.model;

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

/**
 * Metadata for a file kept in object storage (S3 or local disk). Covers both the
 * design's RequestAttachment (uploaded by the student) and CompletedDocument
 * (issued by staff); only the storage key is kept in the database.
 */
@Entity
@Table(name = "stored_files")
public class StoredFile {

    public enum Kind { ATTACHMENT, COMPLETED_DOCUMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private DocumentRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 400)
    private String storageKey;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected StoredFile() {}

    public StoredFile(DocumentRequest request, Kind kind, String fileName, String contentType,
                      long sizeBytes, String storageKey, Instant uploadedAt) {
        this.request = request;
        this.kind = kind;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public DocumentRequest getRequest() { return request; }
    public Kind getKind() { return kind; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public Instant getUploadedAt() { return uploadedAt; }
}
