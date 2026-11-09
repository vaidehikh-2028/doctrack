package com.lnmiit.docreq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.web.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Stateless JWT security. Every endpoint except login and the static UI requires
 * authentication (BR-001, NFR-003); URL rules plus method-level @PreAuthorize enforce
 * role-based access (FR-005, NFR-004).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                           ObjectMapper mapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer tokens, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/favicon.svg",
                                "/error", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/staff/**").hasRole("STAFF")
                        .requestMatchers("/api/approvals/**").hasRole("APPROVER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            boolean expired = Boolean.TRUE.equals(req.getAttribute(JwtAuthFilter.EXPIRED_ATTRIBUTE));
                            write(res, mapper, expired ? ErrorCode.AUTH_SESSION_EXPIRED : ErrorCode.AUTH_INVALID_CREDENTIALS,
                                    expired ? ErrorCode.AUTH_SESSION_EXPIRED.defaultMessage() : "Please log in to continue.");
                        })
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, mapper, ErrorCode.AUTH_ACCESS_DENIED, ErrorCode.AUTH_ACCESS_DENIED.defaultMessage())))
                .addFilterBefore(new JwtAuthFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, ErrorCode code, String message)
            throws IOException {
        res.setStatus(code.httpStatus());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiError.of(code, message));
    }
}
