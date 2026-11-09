package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.Enums.AccountStatus;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.model.DocumentType;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.DocumentTypeRepository;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.web.dto.Dtos;
import com.lnmiit.docreq.web.dto.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** UC-002 Manage Users, UC-003 Manage Document Types and UC-016 Reports (BR-010). */
@Service
public class AdminService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final UserRepository users;
    private final DocumentTypeRepository types;
    private final DocumentRequestRepository requests;
    private final PasswordEncoder encoder;

    public AdminService(UserRepository users, DocumentTypeRepository types, DocumentRequestRepository requests,
                        PasswordEncoder encoder) {
        this.users = users;
        this.types = types;
        this.requests = requests;
        this.encoder = encoder;
    }

    // ---------- users ----------

    @Transactional(readOnly = true)
    public List<Dtos.UserView> listUsers() {
        return users.findAllByOrderByRoleAscUsernameAsc().stream().map(Views::user).toList();
    }

    @Transactional
    public Dtos.UserView createUser(AuthUser admin, Dtos.CreateUserCommand cmd) {
        if (users.existsByUsernameIgnoreCase(cmd.username()) || users.existsByEmailIgnoreCase(cmd.email())) {
            throw new DomainException(ErrorCode.GEN_CONFLICT, "A user with this username or email already exists.");
        }
        User u = users.save(new User(cmd.username().trim(), cmd.name().trim(), cmd.email().trim(),
                cmd.contactInfo(), cmd.role(), encoder.encode(cmd.password())));
        audit.info("admin={} created user={} role={}", admin.username(), u.getUsername(), u.getRole());
        return Views.user(u);
    }

    @Transactional
    public Dtos.UserView updateUser(AuthUser admin, Long userId, Dtos.UpdateUserCommand cmd) {
        User u = users.findById(userId).orElseThrow(() -> new DomainException(ErrorCode.GEN_NOT_FOUND, "User not found."));
        if (u.getId().equals(admin.id()) && (cmd.accountStatus() == AccountStatus.INACTIVE
                || (cmd.role() != null && cmd.role() != u.getRole()))) {
            throw new DomainException(ErrorCode.GEN_BAD_INPUT, "You cannot deactivate or change the role of your own account.");
        }
        if (cmd.name() != null && !cmd.name().isBlank()) u.setName(cmd.name().trim());
        if (cmd.contactInfo() != null) u.setContactInfo(cmd.contactInfo());
        if (cmd.role() != null) u.setRole(cmd.role());
        if (cmd.accountStatus() != null) u.setAccountStatus(cmd.accountStatus());
        audit.info("admin={} updated user={} role={} status={}", admin.username(), u.getUsername(), u.getRole(),
                u.getAccountStatus());
        return Views.user(u);
    }

    // ---------- document types ----------

    @Transactional(readOnly = true)
    public List<Dtos.DocumentTypeView> listTypes() {
        return types.findAllByOrderByCodeAsc().stream().map(Views::type).toList();
    }

    @Transactional
    public Dtos.DocumentTypeView createType(AuthUser admin, Dtos.DocumentTypeCommand cmd) {
        if (types.existsByCode(cmd.code().trim())) {
            throw new DomainException(ErrorCode.GEN_CONFLICT, "A document type with this code already exists.");
        }
        DocumentType t = new DocumentType(cmd.code().trim(), cmd.name().trim(), cmd.description(),
                cmd.requiredFields().stream().map(String::trim).toList(),
                cmd.attachmentRequired(), cmd.approvalRequired(), cmd.processingDays());
        if (cmd.active() != null) t.setActive(cmd.active());
        t = types.save(t);
        audit.info("admin={} created documentType={}", admin.username(), t.getCode());
        return Views.type(t);
    }

    /**
     * Updates a document type. Deactivating it stops new submissions immediately (TC-08);
     * existing requests keep going.
     */
    @Transactional
    public Dtos.DocumentTypeView updateType(AuthUser admin, Long id, Dtos.DocumentTypeCommand cmd) {
        DocumentType t = types.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.GEN_NOT_FOUND, "Document type not found."));
        t.setName(cmd.name().trim());
        t.setDescription(cmd.description());
        t.setRequiredFields(cmd.requiredFields().stream().map(String::trim).toList());
        t.setAttachmentRequired(cmd.attachmentRequired());
        t.setApprovalRequired(cmd.approvalRequired());
        t.setProcessingDays(cmd.processingDays());
        if (cmd.active() != null) t.setActive(cmd.active());
        audit.info("admin={} updated documentType={} active={}", admin.username(), t.getCode(), t.isActive());
        return Views.type(t);
    }

    // ---------- reports (FR-030, FR-031) ----------

    @Transactional(readOnly = true)
    public Dtos.ReportSummary summary() {
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus s : RequestStatus.values()) byStatus.put(s, 0L);
        long total = 0;
        for (Object[] row : requests.countByStatus()) {
            long c = ((Number) row[1]).longValue();
            byStatus.put((RequestStatus) row[0], c);
            total += c;
        }
        List<DocumentRequest> completed = requests.findCompleted();
        Double avgHours = completed.isEmpty() ? null : completed.stream()
                .mapToLong(r -> Duration.between(r.getSubmittedAt(), r.getCompletedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        long escalated = requests.countByEscalatedAtIsNotNullAndStatusIn(EnumSet.of(RequestStatus.SUBMITTED,
                RequestStatus.FORWARDED, RequestStatus.APPROVED, RequestStatus.PROCESSING));
        return new Dtos.ReportSummary(total, byStatus, avgHours, escalated);
    }
}
