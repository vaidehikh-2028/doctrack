package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.domain.RequestValidator;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.model.DocumentType;
import com.lnmiit.docreq.model.StoredFile;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.notification.NotificationManager;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.DocumentTypeRepository;
import com.lnmiit.docreq.repository.NotificationRepository;
import com.lnmiit.docreq.repository.StoredFileRepository;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.storage.FileStorage;
import com.lnmiit.docreq.web.dto.Dtos;
import com.lnmiit.docreq.web.dto.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request Management Service (ISubmitRequest): UC-004 to UC-007, resubmission of
 * incomplete requests and UC-014 document download.
 */
@Service
public class RequestService {

    private final DocumentRequestRepository requests;
    private final DocumentTypeRepository types;
    private final StoredFileRepository files;
    private final NotificationRepository notificationRepo;
    private final NotificationManager notifications;
    private final RequestSupport support;
    private final FileStorage storage;
    private final Clock clock;

    public RequestService(DocumentRequestRepository requests, DocumentTypeRepository types, StoredFileRepository files,
                          NotificationRepository notificationRepo, NotificationManager notifications,
                          RequestSupport support, FileStorage storage, Clock clock) {
        this.requests = requests;
        this.types = types;
        this.files = files;
        this.notificationRepo = notificationRepo;
        this.notifications = notifications;
        this.support = support;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Dtos.DocumentTypeView> offeredTypes() {
        return types.findByActiveTrueOrderByNameAsc().stream().map(Views::type).toList();
    }

    /**
     * UC-005. Validation happens before anything is written, and the request, its
     * history, attachment and notification are saved in one transaction: either all
     * of it is stored or none of it is (NFR-008, E-SYS-500).
     */
    @Transactional
    public Dtos.SubmissionReceipt submit(AuthUser actor, Long documentTypeId, String purpose,
                                         Map<String, String> fieldValues, UploadedFile file) {
        DocumentType type = documentTypeId == null ? null : types.findById(documentTypeId).orElse(null);
        RequestValidator.validateSubmission(type == null ? null : type.toSpec(), purpose, fieldValues,
                file == null ? null : file.toCandidate());

        Instant now = clock.instant();
        User student = support.user(actor);
        DocumentRequest r = requests.save(
                DocumentRequest.submit(student, type, purpose, onlyDeclaredFields(type, fieldValues), now));
        r.assignRequestNumber();
        if (file != null) {
            support.store(r, StoredFile.Kind.ATTACHMENT, file, now);
        }
        notifications.notify(student, NotificationType.SUBMISSION_CONFIRMATION,
                "Your request " + r.getRequestNumber() + " for " + type.getName() + " has been submitted.",
                r.getRequestNumber());
        requests.flush();
        return new Dtos.SubmissionReceipt(r.getRequestNumber(), r.getStatus(), r.getSubmittedAt());
    }

    /** Student corrects an INCOMPLETE request; it goes back to SUBMITTED (state diagram). */
    @Transactional
    public Dtos.RequestDetail resubmit(AuthUser actor, String requestNumber, String purpose,
                                       Map<String, String> fieldValues, UploadedFile file) {
        DocumentRequest r = support.findForUpdate(requestNumber);
        if (!r.isOwnedBy(actor.id())) {
            throw new DomainException(ErrorCode.REQ_NOT_FOUND); // BR-008
        }
        if (r.getStatus() != RequestStatus.INCOMPLETE) {
            throw new DomainException(ErrorCode.VER_INVALID_REQUEST_STATE,
                    "Only requests marked Incomplete can be resubmitted.");
        }
        DocumentType type = r.getDocumentType();
        boolean needsNewFile = type.isAttachmentRequired() && !support.hasFile(r, StoredFile.Kind.ATTACHMENT);
        RequestValidator.TypeSpec spec = new RequestValidator.TypeSpec(true, type.getRequiredFields(), needsNewFile);
        RequestValidator.validateSubmission(spec, purpose, fieldValues, file == null ? null : file.toCandidate());

        Instant now = clock.instant();
        User student = support.user(actor);
        r.updateDetails(purpose, onlyDeclaredFields(type, fieldValues));
        r.setMissingInfo(null);
        r.transitionTo(RequestStatus.SUBMITTED, student, "Resubmitted with the requested information", now);
        if (file != null) {
            support.store(r, StoredFile.Kind.ATTACHMENT, file, now);
        }
        notifications.notify(student, NotificationType.STATUS_CHANGE,
                "Your request " + r.getRequestNumber() + " has been resubmitted.", r.getRequestNumber());
        requests.saveAndFlush(r);
        return support.detail(r);
    }

    @Transactional(readOnly = true)
    public List<Dtos.RequestSummary> myRequests(AuthUser actor) {
        return requests.findByStudentIdOrderBySubmittedAtDesc(actor.id()).stream().map(Views::summary).toList();
    }

    @Transactional(readOnly = true)
    public Dtos.RequestDetail detail(AuthUser actor, String requestNumber) {
        return support.detail(support.findVisible(actor, requestNumber));
    }

    /**
     * Downloads an attachment or the completed document. The completed document is
     * available only to the requesting student (BR-009); attachments to anyone who
     * can see the request.
     */
    @Transactional(readOnly = true)
    public FileDownload download(AuthUser actor, String requestNumber, Long fileId) {
        DocumentRequest r = support.findVisible(actor, requestNumber);
        StoredFile f = files.findById(fileId)
                .filter(x -> x.getRequest().getId().equals(r.getId()))
                .orElseThrow(() -> new DomainException(ErrorCode.GEN_NOT_FOUND, "File not found."));
        if (f.getKind() == StoredFile.Kind.COMPLETED_DOCUMENT && actor.role() != UserRole.STUDENT) {
            throw new DomainException(ErrorCode.AUTH_ACCESS_DENIED,
                    "Only the requesting student can download the completed document.");
        }
        return new FileDownload(f.getFileName(), f.getContentType(), storage.get(f.getStorageKey()));
    }

    @Transactional(readOnly = true)
    public List<Dtos.NotificationView> myNotifications(AuthUser actor) {
        return notificationRepo.findByRecipientIdOrderByCreatedAtDesc(actor.id()).stream()
                .map(Views::notification).toList();
    }

    /** Keeps only the fields the document type declares, trimmed, in declared order. */
    private static Map<String, String> onlyDeclaredFields(DocumentType type, Map<String, String> values) {
        Map<String, String> clean = new LinkedHashMap<>();
        for (String field : type.getRequiredFields()) {
            String v = values == null ? null : values.get(field);
            if (v != null) {
                clean.put(field, v.trim());
            }
        }
        return clean;
    }

    public record FileDownload(String fileName, String contentType, byte[] bytes) {}
}
