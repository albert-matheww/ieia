package com.ieia.security;

import com.ieia.common.exception.ApiException;
import com.ieia.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Static access to the authenticated {@link UserPrincipal}. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public static UserPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(
                () -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
    }

    public static UUID currentUserId() {
        return requirePrincipal().id();
    }

    public static boolean hasRole(Role role) {
        return currentPrincipal().map(p -> p.role() == role).orElse(false);
    }
}
