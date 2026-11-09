package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.ApprovalDecision;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.domain.RequestValidator;
import com.lnmiit.docreq.model.Approval;
import com.lnmiit.docreq.model.DocumentRequest;
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
import java.util.List;

/** Approval Service (IApproveRequest): UC-011 approve and UC-012 reject. */
@Service
public class ApprovalService {

    private final DocumentRequestRepository requests;
    private final ApprovalRepository approvals;
    private final NotificationManager notifications;
    private final RequestSupport support;
    private final Clock clock;

    public ApprovalService(DocumentRequestRepository requests, ApprovalRepository approvals,
                           NotificationManager notifications, RequestSupport support, Clock clock) {
        this.requests = requests;
        this.approvals = approvals;
        this.notifications = notifications;
        this.support = support;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Dtos.RequestSummary> pending() {
        return approvals.findByDecisionOrderByCreatedAtAsc(ApprovalDecision.PENDING).stream()
                .map(a -> Views.summary(a.getRequest())).toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.ApprovalView> myDecisions(AuthUser actor) {
        return approvals.findByDecidedByIdOrderByDecidedAtDesc(actor.id()).stream().map(Views::approval).toList();
    }

    /**
     * Records the decision. The request row is locked and both the Approval and the request
     * are version-checked, so if two approvers decide at once only one decision is stored (TC-20).
     */
    @Transactional
    public Dtos.RequestDetail decide(AuthUser actor, String requestNumber, Dtos.DecisionCommand cmd) {
        DocumentRequest r = support.findForUpdate(requestNumber);
        if (r.getStatus() != RequestStatus.FORWARDED) {
            throw new DomainException(ErrorCode.APR_INVALID_OR_ALREADY_DECIDED);
        }
        Approval approval = approvals.findFirstByRequestIdOrderByCreatedAtDesc(r.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.APR_INVALID_OR_ALREADY_DECIDED));
        User approver = support.user(actor);
        Instant now = clock.instant();
        String number = r.getRequestNumber();

        switch (cmd.decision()) {
            case APPROVED -> {
                approval.approve(approver, cmd.comments(), now);
                r.transitionTo(RequestStatus.APPROVED, approver,
                        RequestValidator.isBlank(cmd.comments()) ? "Approved" : cmd.comments().trim(), now);
                notifications.notify(r.getStudent(), NotificationType.APPROVAL_DECISION,
                        "Your request " + number + " has been approved.", number);
                notifications.notifyRole(UserRole.STAFF, NotificationType.STATUS_CHANGE,
                        "Request " + number + " was approved. Please prepare the document.", number);
            }
            case REJECTED -> {
                approval.reject(approver, cmd.reason(), now); // E-APR-002 if the reason is blank
                String reason = cmd.reason().trim();
                r.transitionTo(RequestStatus.REJECTED, approver, "Rejected: " + reason, now);
                notifications.notify(r.getStudent(), NotificationType.APPROVAL_DECISION,
                        "Your request " + number + " was rejected. Reason: " + reason, number);
            }
            case PENDING -> throw new DomainException(ErrorCode.GEN_BAD_INPUT, "Decision must be APPROVED or REJECTED.");
        }
        approvals.saveAndFlush(approval);
        requests.saveAndFlush(r);
        return support.detail(r);
    }
}
