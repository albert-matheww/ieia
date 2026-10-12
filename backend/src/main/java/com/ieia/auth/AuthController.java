package com.ieia.auth;

import com.ieia.auth.dto.AuthResponse;
import com.ieia.auth.dto.LoginRequest;
import com.ieia.auth.dto.RegisterRequest;
import com.ieia.auth.dto.TokenRequest;
import com.ieia.common.dto.ApiResponse;
import com.ieia.security.SecurityUtils;
import com.ieia.user.UserRepository;
import com.ieia.user.dto.UserProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req,
                                                              HttpServletRequest http) {
        AuthResponse res = authService.register(req, http.getHeader("User-Agent"), clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(res));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, http.getHeader("User-Agent"), clientIp(http)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody TokenRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.refresh(req.refreshToken(), http.getHeader("User-Agent"), clientIp(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody TokenRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileDto> me() {
        var principal = SecurityUtils.requirePrincipal();
        UserProfileDto dto = userRepository.findByIdWithOrganization(principal.id())
                .map(UserProfileDto::from)
                .orElseThrow(() -> com.ieia.common.exception.ApiException.notFound("User"));
        return ApiResponse.ok(dto);
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
