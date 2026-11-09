package com.lnmiit.docreq.security;

import com.lnmiit.docreq.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Issues and verifies HS256-signed JWTs carrying the user id and role. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.ttl:PT2H}") Duration ttl,
                      Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the user id in the token
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired (E-AUTH-440)
     * @throws JwtException if the token is invalid in any other way
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    public long ttlSeconds() {
        return ttl.getSeconds();
    }
}
