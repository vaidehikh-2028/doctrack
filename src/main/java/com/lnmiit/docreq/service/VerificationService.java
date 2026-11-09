package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.domain.RequestValidator;
import com.lnmiit.docreq.model.Approval;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.model.StoredFile;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.notification.NotificationManager;
import com.lnmiit.docreq.repository.ApprovalRepository;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.web.dto.Dtos;
import com.lnmiit.docreq.web.dto.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Verification Service (IVerifyRequest): UC-008 verify, UC-010 forward for approval,
 * UC-009 update status and UC-013 mark document ready.
 */
@Service
public class VerificationService {

    private final DocumentRequestRepository requests;
    private final ApprovalRepository approvals;
    private final NotificationManager notifications;
    private final RequestSupport support;
    private final Clock clock;

    public VerificationService(DocumentRequestRepository requests, ApprovalRepository approvals,
                               NotificationManager notifications, RequestSupport support, Clock clock) {
        this.requests = requests;
        this.approvals = approvals;
        this.notifications = notifications;
        this.support = support;
        this.clock = clock;
    }

    /** Staff work queue, oldest first. With no filter it shows everything staff can act on. */
    @Transactional(readOnly = true)
    public List<Dtos.RequestSummary> queue(List<RequestStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(RequestStatus.SUBMITTED, RequestStatus.APPROVED, RequestStatus.PROCESSING);
        }
        return requests.findByStatusInOrderBySubmittedAtAsc(EnumSet.copyOf(statuses)).stream()
                .map(Views::summary).toList();
    }

    /**
     * UC-008. VERIFIED moves on automatically: to FORWARDED (with a PENDING approval)
     * when the document type needs approval, otherwise straight to PROCESSING (BR-004).
     */
    @Transactional
    public Dtos.RequestDetail verify(AuthUser actor, String requestNumber, Dtos.VerifyCommand cmd) {
        DocumentRequest r = support.findForUpdate(requestNumber);
        if (r.getStatus() != RequestStatus.SUBMITTED) {
            throw new DomainException(ErrorCode.VER_INVALID_REQUEST_STATE,
                    "Request " + requestNumber + " is no longer awaiting verification (current status: "
                            + r.getStatus().label() + ").");
        }
        User staff = support.user(actor);
        Instant now = clock.instant();
        String number = r.getRequestNumber();

        if (cmd.outcome() == Dtos.VerifyOutcome.INCOMPLETE) {
            if (RequestValidator.isBlank(cmd.missingInfo())) {
                throw new DomainException(ErrorCode.VER_MISSING_INFO_REQUIRED);
            }
            String missing = cmd.missingInfo().trim();
            r.setMissingInfo(missing);
            r.transitionTo(RequestStatus.INCOMPLETE, staff, "Missing information: " + missing, now);
            notifications.notify(r.getStudent(), NotificationType.INCOMPLETE_REQUEST,
                    "Your request " + number + " needs more information: " + missing, number);
        } else {
            List<String> missing = RequestValidator.missingMandatoryFields(
                    r.getDocumentType().getRequiredFields(), r.getFieldValues());
            if (!missing.isEmpty()) {
                throw new DomainException(ErrorCode.VER_REQUEST_DATA_INCOMPLETE,
                        "Missing mandatory details: " + String.join(", ", missing)
                                + ". Mark the request Incomplete instead.");
            }
            r.setMissingInfo(null);
            r.transitionTo(RequestStatus.VERIFIED, staff, orDefault(cmd.remarks(), "Details verified"), now);
            if (r.getDocumentType().isApprovalRequired()) {
                r.transitionTo(RequestStatus.FORWARDED, staff, "Forwarded to approving authority", now);
                approvals.save(new Approval(r, now));
                notifications.notify(r.getStudent(), NotificationType.STATUS_CHANGE,
                        "Your request " + number + " has been verified and forwarded for approval.", number);
            } else {
                r.transitionTo(RequestStatus.PROCESSING, staff, "Document preparation started", now);
                notifications.notify(r.getStudent(), NotificationType.STATUS_CHANGE,
                        "Your request " + number + " has been verified and is being processed.", number);
            }
        }
        // Flush inside the method so any version conflict surfaces here as E-SYS-409.
        requests.saveAndFlush(r);
        return support.detail(r);
    }

    /** APPROVED -> PROCESSING. Refused for any other state, e.g. REJECTED (BR-006, TC-21). */
    @Transactional
    public Dtos.RequestDetail startProcessing(AuthUser actor, String requestNumber, String remarks) {
        DocumentRequest r = support.findForUpdate(requestNumber);
        r.transitionTo(RequestStatus.PROCESSING, support.user(actor),
                orDefault(remarks, "Document preparation started"), clock.instant());
        notifications.notify(r.getStudent(), NotificationType.STATUS_CHANGE,
                "Your request " + r.getRequestNumber() + " is being processed.", r.getRequestNumber());
        requests.saveAndFlush(r);
        return support.detail(r);
    }

    /** UC-013: PROCESSING -> COMPLETED with the issued document attached (FR-023, FR-027). */
    @Transactional
    public Dtos.RequestDetail complete(AuthUser actor, String requestNumber, UploadedFile document, String remarks) {
        DocumentRequest r = support.findForUpdate(requestNumber);
        if (document == null) {
            throw new DomainException(ErrorCode.REQ_INVALID_ATTACHMENT, "Upload the completed document.");
        }
        RequestValidator.validateAttachment(document.toCandidate());
        Instant now = clock.instant();
        r.transitionTo(RequestStatus.COMPLETED, support.user(actor), orDefault(remarks, "Document ready"), now);
        support.store(r, StoredFile.Kind.COMPLETED_DOCUMENT, document, now);
        notifications.notify(r.getStudent(), NotificationType.DOCUMENT_READY,
                "Your " + r.getDocumentType().getName() + " for request " + r.getRequestNumber()
                        + " is ready to download.", r.getRequestNumber());
        requests.saveAndFlush(r);
        return support.detail(r);
    }

    private static String orDefault(String s, String fallback) {
        return RequestValidator.isBlank(s) ? fallback : s.trim();
    }
}
