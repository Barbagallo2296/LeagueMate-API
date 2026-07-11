package com.leaguemate.api.service;

import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("manuel22");
        user.setEmail("manuel@test.com");
        user.setPassword("hashedPassword");
        user.setFirstName("Manuel");
        user.setLastName("Barbagallo");
        user.setRole(Role.USER);
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(user.getUsername())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        User saved = userService.registerUser(user);

        assertNotNull(saved);
        assertEquals("manuel22", saved.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_ThrowsConflict_WhenEmailExists() {
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> userService.registerUser(user));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_ThrowsConflict_WhenUsernameExists() {
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(user.getUsername())).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> userService.registerUser(user));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findByUsername_Success() {
        when(userRepository.findByUsername("manuel22")).thenReturn(Optional.of(user));

        User found = userService.findByUsername("manuel22");

        assertNotNull(found);
        assertEquals("manuel22", found.getUsername());
    }

    @Test
    void findByUsername_ThrowsNotFound_WhenUserDoesNotExist() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findByUsername("unknown"));
    }
}