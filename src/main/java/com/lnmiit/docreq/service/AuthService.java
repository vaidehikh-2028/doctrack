package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.security.JwtService;
import com.lnmiit.docreq.web.dto.Dtos;
import com.lnmiit.docreq.web.dto.Views;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** UC-001 Login. Any failure returns the same generic error (FR-002). */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Compared against when the user does not exist, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public Dtos.LoginResponse login(String usernameOrEmail, String password) {
        String id = usernameOrEmail.trim();
        Optional<User> found = id.contains("@") ? users.findByEmailIgnoreCase(id) : users.findByUsernameIgnoreCase(id);
        boolean passwordOk = encoder.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
        User user = found.filter(u -> passwordOk && u.isActive())
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        return new Dtos.LoginResponse(jwt.issue(user), jwt.ttlSeconds(), Views.user(user));
    }
}
