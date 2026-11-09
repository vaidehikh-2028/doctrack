package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.model.StoredFile;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.ApprovalRepository;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.StoredFileRepository;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.storage.FileStorage;
import com.lnmiit.docreq.web.dto.Dtos;
import com.lnmiit.docreq.web.dto.Views;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Lookups and file handling shared by the workflow services. */
@Component
public class RequestSupport {

    private final DocumentRequestRepository requests;
    private final UserRepository users;
    private final ApprovalRepository approvals;
    private final StoredFileRepository files;
    private final FileStorage storage;

    public RequestSupport(DocumentRequestRepository requests, UserRepository users, ApprovalRepository approvals,
                          StoredFileRepository files, FileStorage storage) {
        this.requests = requests;
        this.users = users;
        this.approvals = approvals;
        this.files = files;
        this.storage = storage;
    }

    public DocumentRequest find(String requestNumber) {
        return requests.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.REQ_NOT_FOUND));
    }

    /** Same as {@link #find} but row-locked for the rest of the transaction. */
    public DocumentRequest findForUpdate(String requestNumber) {
        return requests.findForUpdate(requestNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.REQ_NOT_FOUND));
    }

    public User user(AuthUser actor) {
        return users.findById(actor.id()).orElseThrow(() -> new DomainException(ErrorCode.AUTH_ACCESS_DENIED));
    }

    /**
     * Loads a request the actor may see (BR-008, NFR-013). Students see only their own;
     * approvers only requests that were forwarded to approval; staff and admins see all.
     * Requests the actor may not see are reported as not found, so their existence is not revealed.
     */
    public DocumentRequest findVisible(AuthUser actor, String requestNumber) {
        DocumentRequest r = find(requestNumber);
        boolean visible = switch (actor.role()) {
            case STUDENT -> r.isOwnedBy(actor.id());
            case APPROVER -> approvals.countByRequestId(r.getId()) > 0;
            case STAFF, ADMIN -> true;
        };
        if (!visible) {
            throw new DomainException(ErrorCode.REQ_NOT_FOUND);
        }
        return r;
    }

    public Dtos.RequestDetail detail(DocumentRequest r) {
        return Views.detail(r, approvals.findByRequestIdOrderByCreatedAtAsc(r.getId()),
                files.findByRequestIdOrderByUploadedAtAsc(r.getId()));
    }

    /** Writes the file to object storage and records its metadata. Runs inside the caller's transaction. */
    public StoredFile store(DocumentRequest r, StoredFile.Kind kind, UploadedFile file, Instant now) {
        String key = "requests/" + r.getRequestNumber() + "/" + kind.name().toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + "-" + file.safeFileName();
        storage.put(key, file.bytes(), file.contentType());
        return files.save(new StoredFile(r, kind, file.fileName(), file.contentType(), file.bytes().length, key, now));
    }

    public boolean hasFile(DocumentRequest r, StoredFile.Kind kind) {
        return files.findFirstByRequestIdAndKindOrderByUploadedAtDesc(r.getId(), kind).isPresent();
    }
}
