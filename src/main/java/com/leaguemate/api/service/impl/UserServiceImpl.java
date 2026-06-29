package com.leaguemate.api.service.impl;

import com.leaguemate.api.entity.User;
import com.leaguemate.api.entity.UserProfile;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }
}