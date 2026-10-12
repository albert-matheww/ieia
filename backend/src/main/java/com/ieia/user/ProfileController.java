package com.ieia.user;

import com.ieia.common.dto.ApiResponse;
import com.ieia.security.SecurityUtils;
import com.ieia.user.dto.ChangePasswordRequest;
import com.ieia.user.dto.UpdateProfileRequest;
import com.ieia.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserProfileDto> get() {
        return ApiResponse.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @PutMapping
    public ApiResponse<UserProfileDto> update(@Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.currentUserId(), req));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(SecurityUtils.currentUserId(), req);
        return ResponseEntity.noContent().build();
    }
}
