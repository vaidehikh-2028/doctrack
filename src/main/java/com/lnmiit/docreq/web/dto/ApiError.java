package com.lnmiit.docreq.web.dto;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;

import java.time.Instant;
import java.util.List;

/** Uniform error body: {"code":"E-REQ-001","message":"...","fieldErrors":[...],"timestamp":"..."} */
public record ApiError(String code, String message, List<DomainException.FieldError> fieldErrors, Instant timestamp) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.code(), message, List.of(), Instant.now());
    }

    public static ApiError of(DomainException e) {
        return new ApiError(e.getErrorCode().code(), e.getMessage(), e.getFieldErrors(), Instant.now());
    }
}
