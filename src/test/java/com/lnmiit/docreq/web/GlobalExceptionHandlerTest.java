package com.lnmiit.docreq.web;

import com.lnmiit.docreq.web.dto.ApiError;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client disconnects are not server errors. Found by ops/logscan.sh on CI load-test traffic:
 * 84 E-SYS-500 log lines against zero 5xx responses, all ClientAbortException.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void clientAbortIsNotReportedAsServerError() {
        assertThat(handler.other(new ClientAbortException(new IOException("Broken pipe")))).isNull();
    }

    @Test
    void unusableAsyncResponseIsNotReportedAsServerError() {
        assertThat(handler.other(new AsyncRequestNotUsableException("ServletOutputStream failed to flush"))).isNull();
    }

    @Test
    void genuineFailuresStillReturn500WithCode() {
        ResponseEntity<ApiError> response = handler.other(new IllegalStateException("boom"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("E-SYS-500");
    }
}
