package com.leaguemate.api.controller;

import com.leaguemate.api.dto.UpdateUserProfileRequest;
import com.leaguemate.api.dto.UpdateUserRoleRequest;
import com.leaguemate.api.dto.UserProfileResponse;
import com.leaguemate.api.dto.UserResponse;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.mapper.UserMapper;
import com.leaguemate.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SORTABLE = Set.of("id", "username", "firstName", "lastName", "role");

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(UserMapper.toResponse(currentUser));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(userService.findAll(SortWhitelist.check(pageable, SORTABLE)).map(UserMapper::toResponse)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        User user = userService.findById(id);

        boolean canSeeEmail = currentUser.getRole() == Role.ADMIN || currentUser.getId().equals(id);
        return ResponseEntity.ok(canSeeEmail ? UserMapper.toResponse(user) : UserMapper.toPublicResponse(user));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        User updated = userService.updateRole(id, request.role());
        return ResponseEntity.ok(UserMapper.toResponse(updated));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserProfileRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        UserProfileResponse updated = userService.updateProfile(id, request, currentUser.getUsername());
        return ResponseEntity.ok(updated);
    }
}
