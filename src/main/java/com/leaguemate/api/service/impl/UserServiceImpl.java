package com.leaguemate.api.service.impl;

import com.leaguemate.api.dto.UpdateUserProfileRequest;
import com.leaguemate.api.dto.UserProfileResponse;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.entity.UserProfile;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResourceConflictException("Email already registered");
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResourceConflictException("Username already taken");
        }

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        user.setProfile(profile);

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateRole(Long id, Role newRole) {
        User user = findById(id);

        if (user.getRole() == newRole) {
            return user;
        }

        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ResourceConflictException("Cannot demote the last remaining ADMIN");
        }

        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findById(userId);
        return toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId,
                                             UpdateUserProfileRequest request,
                                             String requesterUsername) {
        User target = findById(userId);
        User requester = findByUsername(requesterUsername);

        boolean isOwner = target.getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You can only modify your own profile");
        }

        UserProfile profile = target.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(target);
            target.setProfile(profile);
        }

        profile.setBio(request.bio());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setPhoneNumber(request.phoneNumber());

        userRepository.save(target);
        return toProfileResponse(target);
    }

    private UserProfileResponse toProfileResponse(User user) {
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