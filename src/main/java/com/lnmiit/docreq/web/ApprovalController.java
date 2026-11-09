package com.lnmiit.docreq.web;

import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.ApprovalService;
import com.lnmiit.docreq.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Approving Authority endpoints. /api/approvals/** requires ROLE_APPROVER (SecurityConfig). */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/pending")
    public List<Dtos.RequestSummary> pending() {
        return approvals.pending();
    }

    @GetMapping("/history")
    public List<Dtos.ApprovalView> history(@AuthenticationPrincipal AuthUser user) {
        return approvals.myDecisions(user);
    }

    @PostMapping("/{requestNumber}/decision")
    public Dtos.RequestDetail decide(@AuthenticationPrincipal AuthUser user, @PathVariable("requestNumber") String requestNumber,
                                     @Valid @RequestBody Dtos.DecisionCommand body) {
        return approvals.decide(user, requestNumber, body);
    }
}
