package com.ieia.user;

import com.ieia.auth.RefreshTokenRepository;
import com.ieia.common.exception.ApiException;
import com.ieia.common.exception.ErrorCode;
import com.ieia.user.dto.ChangePasswordRequest;
import com.ieia.user.dto.UpdateProfileRequest;
import com.ieia.user.dto.UserProfileDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        return userRepository.findByIdWithOrganization(userId)
                .map(UserProfileDto::from)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findByIdWithOrganization(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName().trim());
        }
        if (req.country() != null) {
            user.setCountry(req.country().isBlank() ? null : req.country().trim());
        }
        if (req.region() != null && !req.region().isBlank()) {
            user.setRegion(req.region().trim());
        }
        return UserProfileDto.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "New password must be different");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Force re-authentication everywhere after a credential change.
        refreshTokenRepository.revokeAllForUser(userId);
    }
}
