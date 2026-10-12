package com.ieia.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieia.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends PostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void register_then_login_then_access_me_then_refresh_then_logout() {
        String email = uniqueEmail();

        // ---- register ----
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", email,
                "password", "Sup3rSecret!",
                "fullName", "Test User",
                "accountType", "INDIVIDUAL"
        ), JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody().path("success").asBoolean()).isTrue();
        String access = reg.getBody().path("data").path("accessToken").asText();
        String refresh = reg.getBody().path("data").path("refreshToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        assertThat(reg.getBody().path("data").path("user").path("role").asText()).isEqualTo("MEMBER");

        // ---- /me with the access token ----
        ResponseEntity<JsonNode> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(access)), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("data").path("email").asText()).isEqualTo(email);

        // ---- /me without a token → 401 in the envelope ----
        ResponseEntity<JsonNode> meNoAuth = rest.getForEntity("/api/auth/me", JsonNode.class);
        assertThat(meNoAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(meNoAuth.getBody().path("error").path("code").asText()).isEqualTo("UNAUTHENTICATED");

        // ---- login ----
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login", Map.of(
                "email", email, "password", "Sup3rSecret!"), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().path("data").path("accessToken").asText()).isNotBlank();

        // ---- refresh rotates the token ----
        ResponseEntity<JsonNode> refreshed = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", refresh), JsonNode.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = refreshed.getBody().path("data").path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // ---- the OLD refresh token is now rejected ----
        ResponseEntity<JsonNode> reuse = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", refresh), JsonNode.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ---- logout revokes the current refresh token ----
        ResponseEntity<Void> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", newRefresh)), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<JsonNode> afterLogout = rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", newRefresh), JsonNode.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_rejects_duplicate_email_with_409() {
        String email = uniqueEmail();
        Map<String, Object> body = Map.of("email", email, "password", "Sup3rSecret!",
                "fullName", "Dup User", "accountType", "INDIVIDUAL");
        rest.postForEntity("/api/auth/register", body, JsonNode.class);

        ResponseEntity<JsonNode> second = rest.postForEntity("/api/auth/register", body, JsonNode.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().path("error").path("code").asText()).isEqualTo("CONFLICT");
    }

    @Test
    void register_rejects_weak_password_with_400_and_field_errors() {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/auth/register", Map.of(
                "email", uniqueEmail(), "password", "short", "fullName", "Weak",
                "accountType", "INDIVIDUAL"), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(res.getBody().path("error").path("fieldErrors")).isNotEmpty();
    }

    @Test
    void login_with_wrong_password_returns_401() {
        String email = uniqueEmail();
        rest.postForEntity("/api/auth/register", Map.of("email", email, "password", "Sup3rSecret!",
                "fullName", "Wrong Pw", "accountType", "INDIVIDUAL"), JsonNode.class);

        ResponseEntity<JsonNode> res = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "not-the-password"), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void organization_registration_makes_user_org_owner_and_grants_org_endpoints() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", email, "password", "Sup3rSecret!", "fullName", "Owner",
                "accountType", "ORGANIZATION", "organizationName", "Acme Co", "headcount", 42
        ), JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody().path("data").path("user").path("role").asText()).isEqualTo("ORG_OWNER");
        String access = reg.getBody().path("data").path("accessToken").asText();

        ResponseEntity<JsonNode> org = rest.exchange("/api/organization", HttpMethod.GET,
                new HttpEntity<>(bearer(access)), JsonNode.class);
        assertThat(org.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(org.getBody().path("data").path("name").asText()).isEqualTo("Acme Co");
        assertThat(org.getBody().path("data").path("sizeCategory").asText()).isEqualTo("SMALL");
    }

    @Test
    void member_is_forbidden_from_org_endpoints_with_403() {
        String email = uniqueEmail();
        ResponseEntity<JsonNode> reg = rest.postForEntity("/api/auth/register", Map.of(
                "email", email, "password", "Sup3rSecret!", "fullName", "Plain Member",
                "accountType", "INDIVIDUAL"), JsonNode.class);
        String access = reg.getBody().path("data").path("accessToken").asText();

        ResponseEntity<JsonNode> org = rest.exchange("/api/organization", HttpMethod.GET,
                new HttpEntity<>(bearer(access)), JsonNode.class);
        assertThat(org.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(org.getBody().path("error").path("code").asText()).isEqualTo("FORBIDDEN");
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
