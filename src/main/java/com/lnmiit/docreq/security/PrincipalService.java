package com.lnmiit.docreq.security;

import com.lnmiit.docreq.config.CacheConfig;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the user id in a JWT to the principal for the security context. Called on every
 * authenticated request, so the result is cached; {@code AdminService} evicts it when an account's
 * role, name or status changes. An inactive or unknown user resolves to empty and is refused.
 */
@Service
public class PrincipalService {

    private final UserRepository users;

    public PrincipalService(UserRepository users) {
        this.users = users;
    }

    @Cacheable(cacheNames = CacheConfig.PRINCIPALS, key = "#userId")
    public Optional<AuthUser> activePrincipal(Long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .map(u -> new AuthUser(u.getId(), u.getUsername(), u.getName(), u.getRole()));
    }
}
