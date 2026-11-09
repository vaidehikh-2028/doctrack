package com.lnmiit.docreq.web.dto;

import com.lnmiit.docreq.model.Approval;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.model.DocumentType;
import com.lnmiit.docreq.model.Notification;
import com.lnmiit.docreq.model.StatusHistory;
import com.lnmiit.docreq.model.StoredFile;
import com.lnmiit.docreq.model.User;

import java.util.LinkedHashMap;
import java.util.List;

/** Entity-to-DTO mapping. Called inside service transactions so lazy fields are loaded. */
public final class Views {
    private Views() {}

    public static Dtos.UserView user(User u) {
        return new Dtos.UserView(u.getId(), u.getUsername(), u.getName(), u.getEmail(), u.getContactInfo(),
                u.getRole(), u.getAccountStatus());
    }

    public static Dtos.DocumentTypeView type(DocumentType t) {
        return new Dtos.DocumentTypeView(t.getId(), t.getCode(), t.getName(), t.getDescription(),
                List.copyOf(t.getRequiredFields()), t.isAttachmentRequired(), t.isApprovalRequired(),
                t.getProcessingDays(), t.isActive());
    }

    public static Dtos.RequestSummary summary(DocumentRequest r) {
        return new Dtos.RequestSummary(r.getRequestNumber(), r.getDocumentType().getName(),
                r.getDocumentType().getCode(), r.getStudent().getName(), r.getStudent().getUsername(),
                r.getStatus(), r.getSubmittedAt(), r.getUpdatedAt(), r.getEscalatedAt() != null);
    }

    public static Dtos.HistoryView history(StatusHistory h) {
        return new Dtos.HistoryView(h.getStatus(), h.getChangedBy().getName(), h.getChangedBy().getRole(),
                h.getChangedAt(), h.getRemarks());
    }

    public static Dtos.ApprovalView approval(Approval a) {
        return new Dtos.ApprovalView(a.getId(), a.getRequest().getRequestNumber(), a.getDecision(),
                a.getDecidedBy() == null ? null : a.getDecidedBy().getName(),
                a.getComments(), a.getCreatedAt(), a.getDecidedAt());
    }

    public static Dtos.FileView file(StoredFile f) {
        return new Dtos.FileView(f.getId(), f.getKind(), f.getFileName(), f.getContentType(), f.getSizeBytes(),
                f.getUploadedAt());
    }

    public static Dtos.NotificationView notification(Notification n) {
        return new Dtos.NotificationView(n.getId(), n.getType(), n.getMessage(), n.getRequestNumber(),
                n.getCreatedAt(), n.getDeliveryStatus());
    }

    public static Dtos.RequestDetail detail(DocumentRequest r, List<Approval> approvals, List<StoredFile> files) {
        User s = r.getStudent();
        return new Dtos.RequestDetail(r.getRequestNumber(), type(r.getDocumentType()),
                new Dtos.StudentInfo(s.getName(), s.getUsername(), s.getEmail()),
                r.getPurpose(), new LinkedHashMap<>(r.getFieldValues()), r.getStatus(), r.getMissingInfo(),
                r.getSubmittedAt(), r.getUpdatedAt(), r.getCompletedAt(), r.getVersion(),
                r.getStatus().allowedNext(),
                r.getHistory().stream().map(Views::history).toList(),
                approvals.stream().map(Views::approval).toList(),
                files.stream().map(Views::file).toList());
    }
}
