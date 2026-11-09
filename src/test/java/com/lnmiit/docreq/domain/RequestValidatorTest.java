package com.lnmiit.docreq.domain;

import com.lnmiit.docreq.domain.RequestValidator.AttachmentCandidate;
import com.lnmiit.docreq.domain.RequestValidator.TypeSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Equivalence-partitioning and boundary-value tests for UC-005 submission checks. */
class RequestValidatorTest {

    private static final String FIELD = "Program / Semester";
    private static final TypeSpec BONAFIDE = new TypeSpec(true, List.of(FIELD), true);
    private static final Map<String, String> FIELDS = Map.of(FIELD, "B.Tech CSE / Semester 5");
    private static final long FIVE_MB = 5L * 1024 * 1024;

    private static AttachmentCandidate pdf(String name, long size) {
        return new AttachmentCandidate(name, size, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D});
    }

    private static ErrorCode codeOf(Runnable r) {
        try {
            r.run();
            return null;
        } catch (DomainException e) {
            return e.getErrorCode();
        }
    }

    @Test
    @DisplayName("TC-01 valid Bonafide submission is accepted")
    void tc01_valid() {
        assertThatCode(() -> RequestValidator.validateSubmission(BONAFIDE, "Internship application", FIELDS,
                pdf("bonafide_proof.pdf", 200 * 1024))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-02 empty mandatory field -> E-REQ-001 with a message at the field")
    void tc02_mandatoryFieldEmpty() {
        assertThatThrownBy(() -> RequestValidator.validateSubmission(BONAFIDE, "Internship application",
                Map.of(FIELD, "  "), pdf("a.pdf", 1000)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQ_MANDATORY_FIELDS_MISSING);
                    assertThat(e.getMessage()).isEqualTo("Please complete the highlighted fields.");
                    assertThat(e.getFieldErrors()).extracting(DomainException.FieldError::message)
                            .containsExactly("Program / Semester is required.");
                });
    }

    @Test
    @DisplayName("TC-03 purpose of exactly 250 characters is accepted")
    void tc03_purpose250() {
        assertThat(codeOf(() -> RequestValidator.validateSubmission(BONAFIDE, "p".repeat(250), FIELDS,
                pdf("a.pdf", 1000)))).isNull();
    }

    @Test
    @DisplayName("TC-04 purpose of 251 characters -> error on purpose field")
    void tc04_purpose251() {
        assertThatThrownBy(() -> RequestValidator.validateSubmission(BONAFIDE, "p".repeat(251), FIELDS,
                pdf("a.pdf", 1000)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getFieldErrors())
                        .containsExactly(new DomainException.FieldError("purpose",
                                "Purpose must be at most 250 characters.")));
    }

    @Test
    @DisplayName("TC-05 attachment of exactly 5 MB is accepted")
    void tc05_exactly5mb() {
        assertThat(codeOf(() -> RequestValidator.validateSubmission(BONAFIDE, "p", FIELDS,
                pdf("size_5mb.pdf", FIVE_MB)))).isNull();
    }

    @Test
    @DisplayName("TC-06 attachment of 5 MB + 1 KB -> E-REQ-003")
    void tc06_over5mb() {
        assertThat(codeOf(() -> RequestValidator.validateSubmission(BONAFIDE, "p", FIELDS,
                pdf("size_5mb_plus.pdf", FIVE_MB + 1024)))).isEqualTo(ErrorCode.REQ_INVALID_ATTACHMENT);
    }

    @Test
    @DisplayName("TC-07A unsupported file type -> E-REQ-003")
    void tc07a_exe() {
        AttachmentCandidate exe = new AttachmentCandidate("setup.exe", 100 * 1024, new byte[]{0x4D, 0x5A, 0, 0});
        assertThat(codeOf(() -> RequestValidator.validateSubmission(BONAFIDE, "p", FIELDS, exe)))
                .isEqualTo(ErrorCode.REQ_INVALID_ATTACHMENT);
    }

    @Test
    @DisplayName("TC-07B missing attachment -> 'A supporting file is required.'")
    void tc07b_missing() {
        assertThatThrownBy(() -> RequestValidator.validateSubmission(BONAFIDE, "p", FIELDS, null))
                .isInstanceOf(DomainException.class)
                .hasMessage("A supporting file is required.");
    }

    @Test
    @DisplayName("Executable renamed to .pdf is refused by magic-byte check")
    void renamedExecutable() {
        AttachmentCandidate fake = new AttachmentCandidate("proof.pdf", 100, new byte[]{0x4D, 0x5A, 0, 0});
        assertThat(codeOf(() -> RequestValidator.validateAttachment(fake))).isEqualTo(ErrorCode.REQ_INVALID_ATTACHMENT);
    }

    @Test
    @DisplayName("PNG and JPG are accepted, empty file is refused")
    void imageTypes() {
        assertThat(codeOf(() -> RequestValidator.validateAttachment(new AttachmentCandidate("a.PNG", 10,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})))).isNull();
        assertThat(codeOf(() -> RequestValidator.validateAttachment(new AttachmentCandidate("a.jpeg", 10,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})))).isNull();
        assertThat(codeOf(() -> RequestValidator.validateAttachment(new AttachmentCandidate("a.pdf", 0,
                new byte[0])))).isEqualTo(ErrorCode.REQ_INVALID_ATTACHMENT);
    }

    @Test
    @DisplayName("TC-08 withdrawn document type -> E-REQ-002")
    void tc08_typeWithdrawn() {
        TypeSpec withdrawn = new TypeSpec(false, List.of(FIELD), true);
        assertThat(codeOf(() -> RequestValidator.validateSubmission(withdrawn, "p", FIELDS, pdf("a.pdf", 10))))
                .isEqualTo(ErrorCode.REQ_DOCUMENT_TYPE_UNAVAILABLE);
    }

    @Test
    @DisplayName("All field problems are reported together")
    void collectsAllErrors() {
        assertThatThrownBy(() -> RequestValidator.validateSubmission(BONAFIDE, "", Map.of(), null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getFieldErrors()).hasSize(2));
    }

    @Test
    void attachmentOptionalWhenTypeDoesNotNeedIt() {
        TypeSpec noFile = new TypeSpec(true, List.of(FIELD), false);
        assertThat(codeOf(() -> RequestValidator.validateSubmission(noFile, "p", FIELDS, null))).isNull();
    }
}
