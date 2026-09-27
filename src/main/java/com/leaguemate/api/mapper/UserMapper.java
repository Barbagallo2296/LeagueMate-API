package com.leaguemate.api.mapper;

import com.leaguemate.api.dto.RegisterRequest;
import com.leaguemate.api.dto.UserProfileResponse;
import com.leaguemate.api.dto.UserResponse;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.entity.UserProfile;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }

    public static UserResponse toPublicResponse(User user) {
        return new UserResponse(
                user.getId(),
                null,
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }

    public static User fromRegisterRequest(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPassword(request.password());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(Role.USER);
        return user;
    }

    public static UserProfileResponse toProfileResponse(User user) {
        UserProfile profile = user.getProfile();

        if (profile == null) {
            return new UserProfileResponse(null, user.getId(), user.getUsername(), null, null, null);
        }

        return new UserProfileResponse(
                profile.getId(),
                user.getId(),
                user.getUsername(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getPhoneNumber()
        );
    }
}
