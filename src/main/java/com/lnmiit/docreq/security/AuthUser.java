package com.lnmiit.docreq.security;

import com.lnmiit.docreq.domain.Enums.UserRole;

/** The authenticated principal placed in the security context by {@link JwtAuthFilter}. */
public record AuthUser(Long id, String username, String name, UserRole role) {

    public boolean is(UserRole r) { return role == r; }
}
