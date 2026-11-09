package com.lnmiit.docreq.domain;

/**
 * Error catalogue from the interface design (Assignment 7, Section 50.2).
 * Each code carries the HTTP status the REST layer returns for it.
 */
public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS("E-AUTH-401", 401, "Invalid credentials. Please check your details and try again."),
    AUTH_ACCESS_DENIED("E-AUTH-403", 403, "You do not have permission to perform this action."),
    AUTH_SESSION_EXPIRED("E-AUTH-440", 401, "Your session has expired. Please log in again."),

    REQ_MANDATORY_FIELDS_MISSING("E-REQ-001", 400, "Please complete the highlighted fields."),
    REQ_DOCUMENT_TYPE_UNAVAILABLE("E-REQ-002", 409, "The selected document type is no longer offered."),
    REQ_INVALID_ATTACHMENT("E-REQ-003", 400, "Only PDF, JPG or PNG files up to 5 MB are allowed."),
    REQ_NOT_FOUND("E-REQ-004", 404, "Request not found."),

    VER_INVALID_REQUEST_STATE("E-VER-001", 409, "The request is not in a state that allows this action."),
    VER_MISSING_INFO_REQUIRED("E-VER-002", 400, "Specify the missing information."),
    VER_REQUEST_DATA_INCOMPLETE("E-VER-003", 400, "The request is missing mandatory details and cannot be verified."),

    APR_INVALID_OR_ALREADY_DECIDED("E-APR-001", 409, "The request is not awaiting approval."),
    APR_REJECTION_REASON_REQUIRED("E-APR-002", 400, "A reason is required to reject a request."),

    SYS_CONCURRENT_UPDATE("E-SYS-409", 409, "This record was changed by someone else. Reload and try again."),
    SYS_PERSISTENCE_FAILURE("E-SYS-500", 500, "The action could not be completed. Nothing was changed. Please try again."),

    NTF_DELIVERY_FAILED("E-NTF-001", 502, "Notification could not be delivered."),

    GEN_BAD_INPUT("E-GEN-400", 400, "The input is not valid."),
    GEN_NOT_FOUND("E-GEN-404", 404, "Not found."),
    GEN_CONFLICT("E-GEN-409", 409, "The value already exists.");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public String defaultMessage() { return defaultMessage; }
}
