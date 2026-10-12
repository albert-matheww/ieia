package com.ieia.security;

/**
 * Application roles (docs/01-architecture.md §6).
 * <ul>
 *   <li>{@code ADMIN} — manages reference data (activity types, emission factors,
 *       benchmarks, scoring config) and maintenance endpoints.</li>
 *   <li>{@code ORG_OWNER} — everything a MEMBER can do, plus organisation-wide
 *       aggregate views for their organisation.</li>
 *   <li>{@code MEMBER} — normal user; full CRUD on their own data.</li>
 *   <li>{@code VIEWER} — read-only access to their own data.</li>
 * </ul>
 * Spring Security authorities are the role name prefixed with {@code ROLE_}.
 */
public enum Role {
    ADMIN,
    ORG_OWNER,
    MEMBER,
    VIEWER;

    public String authority() {
        return "ROLE_" + name();
    }
}
