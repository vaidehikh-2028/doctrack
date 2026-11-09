package com.lnmiit.docreq.web.dto;

import com.lnmiit.docreq.domain.Enums.AccountStatus;
import com.lnmiit.docreq.domain.Enums.ApprovalDecision;
import com.lnmiit.docreq.domain.Enums.DeliveryStatus;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.model.StoredFile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Request and response bodies of the REST API. Entities are never exposed directly. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, long expiresInSeconds, UserView user) {}

    // ---------- users ----------
    public record UserView(Long id, String username, String name, String email, String contactInfo,
                           UserRole role, AccountStatus accountStatus) {}

    public record CreateUserCommand(@NotBlank @Size(max = 50) String username,
                                    @NotBlank @Size(max = 100) String name,
                                    @NotBlank @Email String email,
                                    @Size(max = 50) String contactInfo,
                                    @NotNull UserRole role,
                                    @NotBlank @Size(min = 8, max = 72) String password) {}

    public record UpdateUserCommand(@Size(max = 100) String name, @Size(max = 50) String contactInfo,
                                    UserRole role, AccountStatus accountStatus) {}

    // ---------- document types ----------
    public record DocumentTypeView(Long id, String code, String name, String description, List<String> requiredFields,
                                   boolean attachmentRequired, boolean approvalRequired, int processingDays,
                                   boolean active) {}

    public record DocumentTypeCommand(@NotBlank @Size(max = 10) String code,
                                      @NotBlank @Size(max = 100) String name,
                                      @Size(max = 500) String description,
                                      @NotNull List<@NotBlank @Size(max = 60) String> requiredFields,
                                      boolean attachmentRequired,
                                      boolean approvalRequired,
                                      @Min(1) int processingDays,
                                      Boolean active) {}

    // ---------- requests ----------
    public record SubmissionReceipt(String requestNumber, RequestStatus status, Instant submittedAt) {}

    public record RequestSummary(String requestNumber, String documentType, String documentTypeCode,
                                 String studentName, String studentUsername, RequestStatus status,
                                 Instant submittedAt, Instant updatedAt, boolean escalated) {}

    public record HistoryView(RequestStatus status, String changedBy, UserRole changedByRole,
                              Instant changedAt, String remarks) {}

    public record ApprovalView(Long id, String requestNumber, ApprovalDecision decision, String decidedBy,
                               String comments, Instant createdAt, Instant decidedAt) {}

    public record FileView(Long id, StoredFile.Kind kind, String fileName, String contentType,
                           long sizeBytes, Instant uploadedAt) {}

    public record StudentInfo(String name, String username, String email) {}

    public record RequestDetail(String requestNumber, DocumentTypeView documentType, StudentInfo student,
                                String purpose, Map<String, String> fieldValues, RequestStatus status,
                                String missingInfo, Instant submittedAt, Instant updatedAt, Instant completedAt,
                                Long version, Set<RequestStatus> allowedNext, List<HistoryView> history,
                                List<ApprovalView> approvals, List<FileView> files) {}

    // ---------- staff / approver commands ----------
    public enum VerifyOutcome { VERIFIED, INCOMPLETE }

    public record VerifyCommand(@NotNull VerifyOutcome outcome, @Size(max = 500) String missingInfo,
                                @Size(max = 500) String remarks) {}

    public record DecisionCommand(@NotNull ApprovalDecision decision, @Size(max = 500) String reason,
                                  @Size(max = 500) String comments) {}

    public record RemarksCommand(@Size(max = 500) String remarks) {}

    // ---------- notifications & reports ----------
    public record NotificationView(Long id, NotificationType type, String message, String requestNumber,
                                   Instant createdAt, DeliveryStatus deliveryStatus) {}

    public record ReportSummary(long total, Map<RequestStatus, Long> byStatus, Double averageCompletionHours,
                                long escalatedOpen) {}
}
