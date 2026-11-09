package com.lnmiit.docreq.web;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.List;

/** Maps every failure to the error catalogue so clients always get {code, message}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException e) {
        return ResponseEntity.status(e.getErrorCode().httpStatus()).body(ApiError.of(e));
    }

    /** Two users changed the same record at once; the second write is refused (TC-14, TC-20). */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> concurrentUpdate(ConcurrencyFailureException e) {
        return error(ErrorCode.SYS_CONCURRENT_UPDATE, ErrorCode.SYS_CONCURRENT_UPDATE.defaultMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return error(ErrorCode.AUTH_ACCESS_DENIED, ErrorCode.AUTH_ACCESS_DENIED.defaultMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        List<DomainException.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new DomainException.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(ErrorCode.GEN_BAD_INPUT.code(),
                "Please correct the highlighted fields.", fields, Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return error(ErrorCode.REQ_INVALID_ATTACHMENT, ErrorCode.REQ_INVALID_ATTACHMENT.defaultMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> badInput(Exception e) {
        return error(ErrorCode.GEN_BAD_INPUT, "The request is malformed or missing a required value.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> persistence(DataAccessException e) {
        log.error("E-SYS-500 persistence failure", e);
        return error(ErrorCode.SYS_PERSISTENCE_FAILURE, ErrorCode.SYS_PERSISTENCE_FAILURE.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            // Spring MVC errors such as 404 for an unknown path or 405 for a wrong method
            ErrorCode code = er.getStatusCode().value() == 404 ? ErrorCode.GEN_NOT_FOUND : ErrorCode.GEN_BAD_INPUT;
            return ResponseEntity.status(er.getStatusCode())
                    .body(new ApiError(code.code(), code.defaultMessage(), List.of(), Instant.now()));
        }
        log.error("E-SYS-500 unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.SYS_PERSISTENCE_FAILURE, ErrorCode.SYS_PERSISTENCE_FAILURE.defaultMessage()));
    }

    private static ResponseEntity<ApiError> error(ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus()).body(ApiError.of(code, message));
    }
}
