package com.ieia.security;

import com.ieia.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for the access-token codec — no Spring context, no DB. */
class JwtServiceTest {

    private AppProperties propsWithSecret(String secret) {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(secret);
        props.getJwt().setAccessTtlSeconds(900);
        return props;
    }

    private final UserPrincipal principal = new UserPrincipal(
            UUID.randomUUID(), "alice@example.com", null, Role.MEMBER, UUID.randomUUID(), true);

    @Test
    void issuesAndParsesRoundTrip() {
        JwtService jwt = new JwtService(propsWithSecret("unit-secret-unit-secret-unit-secret-unit-secret-01234"));

        String token = jwt.issueAccessToken(principal);
        var claims = jwt.parse(token).orElseThrow();

        assertThat(claims.userId()).isEqualTo(principal.id());
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.role()).isEqualTo(Role.MEMBER);
        assertThat(claims.organizationId()).isEqualTo(principal.organizationId());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = new JwtService(propsWithSecret("unit-secret-unit-secret-unit-secret-unit-secret-01234"));
        String token = jwt.issueAccessToken(principal);
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        JwtService issuer = new JwtService(propsWithSecret("secret-one-secret-one-secret-one-secret-one-0123456789"));
        JwtService verifier = new JwtService(propsWithSecret("secret-two-secret-two-secret-two-secret-two-0123456789"));

        String token = issuer.issueAccessToken(principal);

        assertThat(verifier.parse(token)).isEmpty();
    }

    @Test
    void refusesToStartWithAShortSecret() {
        assertThatThrownBy(() -> new JwtService(propsWithSecret("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
