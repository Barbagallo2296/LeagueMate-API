package com.leaguemate.api.service;

import com.leaguemate.api.dto.UpdateUserProfileRequest;
import com.leaguemate.api.dto.UserProfileResponse;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    User registerUser(User user);

    User findByUsername(String username);

    User findById(Long id);

    Page<User> findAll(Pageable pageable);

    User updateRole(Long id, Role role);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request, String requesterUsername);
}