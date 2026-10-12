package com.ieia.security;

import com.ieia.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates the short-lived <b>access</b> JWT (HS256).
 * Refresh tokens are opaque and handled by {@code auth/RefreshTokenService}.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ieia.jwt.secret must be set to at least 32 bytes (256 bits). " +
                    "Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = props.getJwt().getIssuer();
        this.accessTtlSeconds = props.getJwt().getAccessTtlSeconds();
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** Build an access token for the given principal. */
    public String issueAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(principal.id().toString())
                .claim("email", principal.email())
                .claim("role", principal.role().name())
                .claim("orgId", principal.organizationId() == null ? null : principal.organizationId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Parse + verify. Returns empty on any problem (expired, tampered, malformed). */
    public Optional<AccessClaims> parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(c.getSubject());
            String orgRaw = c.get("orgId", String.class);
            return Optional.of(new AccessClaims(
                    userId,
                    c.get("email", String.class),
                    Role.valueOf(c.get("role", String.class)),
                    orgRaw == null ? null : UUID.fromString(orgRaw),
                    c.getExpiration().toInstant()
            ));
        } catch (ExpiredJwtException e) {
            log.debug("Access token expired");
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejecting access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record AccessClaims(UUID userId, String email, Role role, UUID organizationId, Instant expiresAt) {}
}
