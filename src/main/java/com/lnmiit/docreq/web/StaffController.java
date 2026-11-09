package com.lnmiit.docreq.web;

import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.VerificationService;
import com.lnmiit.docreq.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Document Processing Staff endpoints. /api/staff/** requires ROLE_STAFF (SecurityConfig). */
@RestController
@RequestMapping("/api/staff/requests")
public class StaffController {

    private final VerificationService verification;

    public StaffController(VerificationService verification) {
        this.verification = verification;
    }

    /** e.g. GET /api/staff/requests?status=SUBMITTED&status=APPROVED */
    @GetMapping
    public List<Dtos.RequestSummary> queue(@RequestParam(name = "status", required = false) List<RequestStatus> statuses) {
        return verification.queue(statuses);
    }

    @PostMapping("/{requestNumber}/verify")
    public Dtos.RequestDetail verify(@AuthenticationPrincipal AuthUser user, @PathVariable("requestNumber") String requestNumber,
                                     @Valid @RequestBody Dtos.VerifyCommand body) {
        return verification.verify(user, requestNumber, body);
    }

    @PostMapping("/{requestNumber}/start-processing")
    public Dtos.RequestDetail startProcessing(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable("requestNumber") String requestNumber,
                                              @Valid @RequestBody(required = false) Dtos.RemarksCommand body) {
        return verification.startProcessing(user, requestNumber, body == null ? null : body.remarks());
    }

    @PostMapping(value = "/{requestNumber}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dtos.RequestDetail complete(@AuthenticationPrincipal AuthUser user, @PathVariable("requestNumber") String requestNumber,
                                       @RequestPart(name = "file", required = false) MultipartFile file,
                                       @RequestParam(name = "remarks", required = false) String remarks) {
        return verification.complete(user, requestNumber, Multipart.file(file), remarks);
    }
}
