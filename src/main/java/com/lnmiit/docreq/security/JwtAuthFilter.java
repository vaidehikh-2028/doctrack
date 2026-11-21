package com.lnmiit.docreq.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer &lt;jwt&gt;", checks the user still exists and is ACTIVE,
 * and puts an {@link AuthUser} in the security context. Deactivated users lose access
 * immediately even if their token has not expired (FR-028): the lookup is cached by
 * {@link PrincipalService}, and deactivation evicts the cached entry.
 *
 * <p>Deliberately not a Spring bean: it is added only to the security filter chain,
 * so the servlet container does not register it a second time.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String EXPIRED_ATTRIBUTE = "docreq.jwt.expired";

    private final JwtService jwtService;
    private final PrincipalService principals;

    public JwtAuthFilter(JwtService jwtService, PrincipalService principals) {
        this.jwtService = jwtService;
        this.principals = principals;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Long userId = jwtService.parseUserId(token);
                principals.activePrincipal(userId).ifPresent(principal -> {
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (ExpiredJwtException e) {
                request.setAttribute(EXPIRED_ATTRIBUTE, Boolean.TRUE);
            } catch (JwtException | IllegalArgumentException e) {
                // invalid token: continue unauthenticated; the entry point answers E-AUTH-401
            }
        }
        chain.doFilter(request, response);
    }
}
