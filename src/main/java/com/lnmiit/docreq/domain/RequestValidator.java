package com.lnmiit.docreq.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Submission checks of UC-005 (Assignment 6, RequestValidator; Assignment 7, IRequestValidator).
 * Pure Java with no framework dependencies so it can be unit-tested in isolation.
 * Every problem is collected so the student sees all missing items together.
 */
public final class RequestValidator {

    public static final int PURPOSE_MAX_LENGTH = 250;
    public static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024; // 5 MB, inclusive (TC-05 / TC-06)
    public static final String PURPOSE_FIELD = "purpose";
    public static final String ATTACHMENT_FIELD = "attachment";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    /** What the validator needs to know about the selected document type. */
    public record TypeSpec(boolean offered, List<String> requiredFields, boolean attachmentRequired) {}

    /** An uploaded file before it is stored. {@code header} holds at least the first few bytes. */
    public record AttachmentCandidate(String fileName, long sizeBytes, byte[] header) {}

    private RequestValidator() {}

    /**
     * Validates a submission and throws a {@link DomainException} with the matching
     * error code on the first failing group: document type (E-REQ-002), then fields
     * (E-REQ-001), then attachment (E-REQ-003).
     */
    public static void validateSubmission(TypeSpec type, String purpose, Map<String, String> fieldValues,
                                          AttachmentCandidate attachment) {
        if (type == null || !type.offered()) {
            throw new DomainException(ErrorCode.REQ_DOCUMENT_TYPE_UNAVAILABLE);
        }

        List<DomainException.FieldError> errors = new ArrayList<>();
        for (String field : missingMandatoryFields(type.requiredFields(), fieldValues)) {
            errors.add(new DomainException.FieldError(field, field + " is required."));
        }
        if (isBlank(purpose)) {
            errors.add(new DomainException.FieldError(PURPOSE_FIELD, "Purpose is required."));
        } else if (purpose.length() > PURPOSE_MAX_LENGTH) {
            errors.add(new DomainException.FieldError(PURPOSE_FIELD,
                    "Purpose must be at most " + PURPOSE_MAX_LENGTH + " characters."));
        }
        if (!errors.isEmpty()) {
            throw new DomainException(ErrorCode.REQ_MANDATORY_FIELDS_MISSING,
                    ErrorCode.REQ_MANDATORY_FIELDS_MISSING.defaultMessage(), errors);
        }

        if (attachment == null) {
            if (type.attachmentRequired()) {
                throw new DomainException(ErrorCode.REQ_INVALID_ATTACHMENT, "A supporting file is required.",
                        List.of(new DomainException.FieldError(ATTACHMENT_FIELD, "A supporting file is required.")));
            }
            return;
        }
        validateAttachment(attachment);
    }

    /** Throws E-REQ-003 unless the file is a real PDF/JPG/PNG of at most 5 MB. */
    public static void validateAttachment(AttachmentCandidate attachment) {
        boolean ok = attachment.sizeBytes() > 0
                && attachment.sizeBytes() <= MAX_ATTACHMENT_BYTES
                && hasAllowedExtension(attachment.fileName())
                && contentMatchesExtension(attachment.fileName(), attachment.header());
        if (!ok) {
            String msg = ErrorCode.REQ_INVALID_ATTACHMENT.defaultMessage();
            throw new DomainException(ErrorCode.REQ_INVALID_ATTACHMENT, msg,
                    List.of(new DomainException.FieldError(ATTACHMENT_FIELD, msg)));
        }
    }

    /** Required fields that are absent or blank. Also used by verification (E-VER-003). */
    public static List<String> missingMandatoryFields(List<String> requiredFields, Map<String, String> values) {
        List<String> missing = new ArrayList<>();
        if (requiredFields == null) {
            return missing;
        }
        for (String field : requiredFields) {
            String v = values == null ? null : values.get(field);
            if (isBlank(v)) {
                missing.add(field);
            }
        }
        return missing;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasAllowedExtension(String fileName) {
        return ALLOWED_EXTENSIONS.contains(extension(fileName));
    }

    /**
     * Checks magic bytes so a renamed executable ("setup.exe" -> "setup.pdf") is refused.
     */
    private static boolean contentMatchesExtension(String fileName, byte[] h) {
        if (h == null) return false;
        return switch (extension(fileName)) {
            case "pdf" -> startsWith(h, new int[]{0x25, 0x50, 0x44, 0x46});          // %PDF
            case "png" -> startsWith(h, new int[]{0x89, 0x50, 0x4E, 0x47});          // .PNG
            case "jpg", "jpeg" -> startsWith(h, new int[]{0xFF, 0xD8, 0xFF});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
