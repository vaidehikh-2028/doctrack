package com.lnmiit.docreq.web;

import com.lnmiit.docreq.security.AuthUser;
import com.lnmiit.docreq.service.AuthService;
import com.lnmiit.docreq.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest body) {
        return auth.login(body.username(), body.password());
    }

    /** Lets the UI restore a session from a stored token. */
    @GetMapping("/me")
    public AuthUser me(@AuthenticationPrincipal AuthUser user) {
        return user;
    }
}
