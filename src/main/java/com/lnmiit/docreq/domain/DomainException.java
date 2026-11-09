package com.lnmiit.docreq.domain;

import java.util.List;

/** A business-rule violation carrying an error code from {@link ErrorCode}. */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldError> fieldErrors;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public DomainException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public DomainException(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    public record FieldError(String field, String message) {}
}
