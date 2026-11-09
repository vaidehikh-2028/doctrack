package com.lnmiit.docreq.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.RequestService;
import com.lnmiit.docreq.web.dto.Dtos;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Student-facing endpoints plus shared request viewing. */
@RestController
public class RequestController {

    private final RequestService requests;
    private final ObjectMapper mapper;

    public RequestController(RequestService requests, ObjectMapper mapper) {
        this.requests = requests;
        this.mapper = mapper;
    }

    @GetMapping("/api/document-types")
    public List<Dtos.DocumentTypeView> documentTypes() {
        return requests.offeredTypes();
    }

    /**
     * Multipart form: documentTypeId, purpose, fields (JSON object of field name to value)
     * and an optional file.
     */
    @PostMapping(value = "/api/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.SubmissionReceipt submit(@AuthenticationPrincipal AuthUser user,
                                         @RequestParam(name = "documentTypeId", required = false) Long documentTypeId,
                                         @RequestParam(name = "purpose", required = false) String purpose,
                                         @RequestParam(name = "fields", required = false) String fieldsJson,
                                         @RequestPart(name = "file", required = false) MultipartFile file) {
        return requests.submit(user, documentTypeId, purpose, Multipart.fields(mapper, fieldsJson),
                Multipart.file(file));
    }

    @PostMapping(value = "/api/requests/{requestNumber}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public Dtos.RequestDetail resubmit(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable("requestNumber") String requestNumber,
                                       @RequestParam(name = "purpose", required = false) String purpose,
                                       @RequestParam(name = "fields", required = false) String fieldsJson,
                                       @RequestPart(name = "file", required = false) MultipartFile file) {
        return requests.resubmit(user, requestNumber, purpose, Multipart.fields(mapper, fieldsJson),
                Multipart.file(file));
    }

    @GetMapping("/api/requests/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Dtos.RequestSummary> mine(@AuthenticationPrincipal AuthUser user) {
        return requests.myRequests(user);
    }

    @GetMapping("/api/requests/{requestNumber}")
    public Dtos.RequestDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable("requestNumber") String requestNumber) {
        return requests.detail(user, requestNumber);
    }

    @GetMapping("/api/requests/{requestNumber}/files/{fileId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthUser user,
                                           @PathVariable("requestNumber") String requestNumber, @PathVariable("fileId") Long fileId) {
        RequestService.FileDownload f = requests.download(user, requestNumber, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.bytes());
    }

    @GetMapping("/api/notifications/mine")
    public List<Dtos.NotificationView> notifications(@AuthenticationPrincipal AuthUser user) {
        return requests.myNotifications(user);
    }
}
