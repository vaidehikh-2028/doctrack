package com.lnmiit.docreq.web;

import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.AdminService;
import com.lnmiit.docreq.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administrator endpoints (BR-010) and reports for staff and admins (FR-030). */
@RestController
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/api/admin/users")
    public List<Dtos.UserView> users() {
        return admin.listUsers();
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.UserView createUser(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody Dtos.CreateUserCommand body) {
        return admin.createUser(user, body);
    }

    @PatchMapping("/api/admin/users/{id}")
    public Dtos.UserView updateUser(@AuthenticationPrincipal AuthUser user, @PathVariable("id") Long id,
                                    @Valid @RequestBody Dtos.UpdateUserCommand body) {
        return admin.updateUser(user, id, body);
    }

    @GetMapping("/api/admin/document-types")
    public List<Dtos.DocumentTypeView> types() {
        return admin.listTypes();
    }

    @PostMapping("/api/admin/document-types")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.DocumentTypeView createType(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody Dtos.DocumentTypeCommand body) {
        return admin.createType(user, body);
    }

    @PutMapping("/api/admin/document-types/{id}")
    public Dtos.DocumentTypeView updateType(@AuthenticationPrincipal AuthUser user, @PathVariable("id") Long id,
                                            @Valid @RequestBody Dtos.DocumentTypeCommand body) {
        return admin.updateType(user, id, body);
    }

    @GetMapping("/api/reports/summary")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Dtos.ReportSummary summary() {
        return admin.summary();
    }
}
