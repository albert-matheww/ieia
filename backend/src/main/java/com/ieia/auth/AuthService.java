package com.ieia.auth;

import com.ieia.auth.dto.AuthResponse;
import com.ieia.auth.dto.LoginRequest;
import com.ieia.auth.dto.RegisterRequest;
import com.ieia.common.exception.ApiException;
import com.ieia.common.exception.ErrorCode;
import com.ieia.common.util.HashUtil;
import com.ieia.config.AppProperties;
import com.ieia.organization.Organization;
import com.ieia.organization.OrganizationRepository;
import com.ieia.security.JwtService;
import com.ieia.security.Role;
import com.ieia.security.UserPrincipal;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import com.ieia.user.dto.UserProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final long refreshTtlSeconds;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       AppProperties props) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTtlSeconds = props.getJwt().getRefreshTtlSeconds();
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, String userAgent, String ip) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setCountry(req.country());
        if (req.region() != null && !req.region().isBlank()) {
            user.setRegion(req.region().trim());
        }
        user.setActive(true);

        if (req.isOrganization()) {
            if (req.organizationName() == null || req.organizationName().isBlank()) {
                throw ApiException.validation("organizationName is required for an ORGANIZATION account");
            }
            Organization org = new Organization();
            org.setName(req.organizationName().trim());
            org.setIndustry(req.industry());
            org.setCountry(req.country());
            if (req.region() != null && !req.region().isBlank()) {
                org.setRegion(req.region().trim());
            }
            org.setHeadcount(req.headcount());
            org.setSizeCategory(sizeFromHeadcount(req.headcount()));
            organizationRepository.save(org);
            user.setOrganization(org);
            user.setRole(Role.ORG_OWNER);
        } else {
            user.setRole(Role.MEMBER);
        }

        userRepository.save(user);
        log.info("Registered user {} ({})", user.getId(), user.getRole());
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent, String ip) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email().trim().toLowerCase(), req.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(req.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid email or password"));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This account is disabled");
        }
        user.setLastLoginAt(Instant.now());
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ip) {
        String hash = HashUtil.sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid refresh token"));

        if (!token.isActive(Instant.now())) {
            // Re-use of a revoked/expired token: revoke the whole family defensively.
            refreshTokenRepository.revokeAllForUser(token.getUser().getId());
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token expired or already used");
        }

        token.setRevoked(true);                       // rotation: old token dies
        User user = token.getUser();
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This account is disabled");
        }
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = HashUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void logoutAll(java.util.UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    // ---------------------------------------------------------------------

    private AuthResponse issueTokens(User user, String userAgent, String ip) {
        UserPrincipal principal = UserPrincipal.from(user);
        String accessToken = jwtService.issueAccessToken(principal);

        String rawRefresh = HashUtil.randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(HashUtil.sha256Hex(rawRefresh));
        rt.setExpiresAt(Instant.now().plus(refreshTtlSeconds, ChronoUnit.SECONDS));
        rt.setUserAgent(trim(userAgent, 255));
        rt.setIpAddress(trim(ip, 64));
        refreshTokenRepository.save(rt);

        return AuthResponse.of(accessToken, jwtService.accessTtlSeconds(), rawRefresh, UserProfileDto.from(user));
    }

    private static Organization.SizeCategory sizeFromHeadcount(Integer headcount) {
        if (headcount == null) return Organization.SizeCategory.SMALL;
        if (headcount <= 1) return Organization.SizeCategory.INDIVIDUAL;
        if (headcount <= 50) return Organization.SizeCategory.SMALL;
        if (headcount <= 250) return Organization.SizeCategory.MEDIUM;
        return Organization.SizeCategory.LARGE;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
