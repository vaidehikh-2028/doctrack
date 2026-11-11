package com.lnmiit.docreq.web;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.time.Instant;
import java.util.List;

/**
 * Maps every failure to the error catalogue so clients always get {code, message}, and logs each one
 * on a single line that ops/logscan.sh can parse:
 * <pre>E-REQ-001 400 POST /api/requests user=7 Please complete the highlighted fields.</pre>
 * No request bodies, credentials or field values are logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException e) {
        return logged(ResponseEntity.status(e.getErrorCode().httpStatus()).body(ApiError.of(e)));
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
        return logged(ResponseEntity.badRequest().body(new ApiError(ErrorCode.GEN_BAD_INPUT.code(),
                "Please correct the highlighted fields.", fields, Instant.now())));
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
        log.error("E-SYS-500 500 {} {} persistence failure", where(), user(), e);
        return error(ErrorCode.SYS_PERSISTENCE_FAILURE, ErrorCode.SYS_PERSISTENCE_FAILURE.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            // Spring MVC errors such as 404 for an unknown path or 405 for a wrong method
            ErrorCode code = er.getStatusCode().value() == 404 ? ErrorCode.GEN_NOT_FOUND : ErrorCode.GEN_BAD_INPUT;
            return logged(ResponseEntity.status(er.getStatusCode())
                    .body(new ApiError(code.code(), code.defaultMessage(), List.of(), Instant.now())));
        }
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            // The client closed the connection mid-response (tab closed, load generator stopped).
            // Nothing failed on our side and there is no one to answer, so this is not an E-SYS-500.
            log.debug("client disconnected during {}: {}", where(), e.getMessage());
            return null;
        }
        log.error("E-SYS-500 500 {} {} unexpected error", where(), user(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.SYS_PERSISTENCE_FAILURE, ErrorCode.SYS_PERSISTENCE_FAILURE.defaultMessage()));
    }

    private static ResponseEntity<ApiError> error(ErrorCode code, String message) {
        ResponseEntity<ApiError> response = ResponseEntity.status(code.httpStatus()).body(ApiError.of(code, message));
        // 5xx responses are already logged above, with their stack trace.
        return code.httpStatus() >= 500 ? response : logged(response);
    }

    /** One WARN line per handled client error: code, status, request, caller, message. */
    private static ResponseEntity<ApiError> logged(ResponseEntity<ApiError> response) {
        ApiError body = response.getBody();
        if (body != null) {
            log.warn("{} {} {} {} {}", body.code(), response.getStatusCode().value(), where(), user(), body.message());
        }
        return response;
    }

    private static String where() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getMethod() + " " + attrs.getRequest().getRequestURI();
        }
        return "- -";
    }

    private static String user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser u ? "user=" + u.id() : "user=anon";
    }
}
