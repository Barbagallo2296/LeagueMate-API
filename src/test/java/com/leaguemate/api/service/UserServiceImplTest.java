package com.leaguemate.api.service;

import com.leaguemate.api.dto.UpdateUserProfileRequest;
import com.leaguemate.api.dto.UserProfileResponse;
import com.leaguemate.api.entity.Role;
import com.leaguemate.api.entity.User;
import com.leaguemate.api.entity.UserProfile;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
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
    private UpdateUserProfileRequest profileRequest;

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

        profileRequest = new UpdateUserProfileRequest(
                "Full Stack Developer",
                "https://avatar.com/manuel.png",
                "+39 333 1234567"
        );
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(user.getUsername())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        User saved = userService.registerUser(user);

        assertNotNull(saved);
        assertEquals("manuel22", saved.getUsername());
        assertNotNull(saved.getProfile());
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

    @Test
    void findById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User found = userService.findById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    void findById_ThrowsNotFound_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findById(99L));
    }

    @Test
    void findAll_ReturnsList() {
        User other = new User();
        other.setId(2L);
        other.setUsername("doc_friend");

        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user, other), pageable, 2));

        Page<User> result = userService.findAll(pageable);

        assertEquals(2, result.getContent().size());
        verify(userRepository, times(1)).findAll(pageable);
    }

    @Test
    void updateRole_Success_PromotesUserToOrganizer() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        User updated = userService.updateRole(1L, Role.ORGANIZER);

        assertEquals(Role.ORGANIZER, updated.getRole());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateRole_DoesNothing_WhenRoleIsUnchanged() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.updateRole(1L, Role.USER);

        assertEquals(Role.USER, result.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateRole_ThrowsConflict_WhenDemotingLastAdmin() {
        user.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(ResourceConflictException.class, () -> userService.updateRole(1L, Role.USER));
        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateRole_Success_WhenOtherAdminsRemain() {
        user.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(3L);
        when(userRepository.save(any(User.class))).thenReturn(user);

        User updated = userService.updateRole(1L, Role.USER);

        assertEquals(Role.USER, updated.getRole());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void getProfile_Success() {
        UserProfile profile = new UserProfile();
        profile.setId(10L);
        profile.setBio("Bio esistente");
        profile.setAvatarUrl("https://avatar.com/old.png");
        profile.setPhoneNumber("+39 000 0000000");
        profile.setUser(user);
        user.setProfile(profile);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertEquals(10L, response.id());
        assertEquals(1L, response.userId());
        assertEquals("manuel22", response.username());
        assertEquals("Bio esistente", response.bio());
    }

    @Test
    void getProfile_ReturnsEmptyFields_WhenProfileIsMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertNull(response.id());
        assertNull(response.bio());
        assertEquals(1L, response.userId());
        assertEquals("manuel22", response.username());
    }

    @Test
    void updateProfile_Success_WhenOwner() {
        UserProfile profile = new UserProfile();
        profile.setId(10L);
        profile.setUser(user);
        user.setProfile(profile);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse response = userService.updateProfile(1L, profileRequest, user);

        assertEquals("Full Stack Developer", response.bio());
        assertEquals("+39 333 1234567", response.phoneNumber());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_ThrowsAccessDenied_WhenNotOwnerAndNotAdmin() {
        User intruder = new User();
        intruder.setId(2L);
        intruder.setUsername("doc_friend");
        intruder.setRole(Role.USER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class,
                () -> userService.updateProfile(1L, profileRequest, intruder));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfile_Success_WhenAdminEditsAnotherUser() {
        UserProfile profile = new UserProfile();
        profile.setId(10L);
        profile.setUser(user);
        user.setProfile(profile);

        User admin = new User();
        admin.setId(2L);
        admin.setUsername("bossman");
        admin.setRole(Role.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse response = userService.updateProfile(1L, profileRequest, admin);

        assertEquals("Full Stack Developer", response.bio());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_CreatesProfile_WhenMissing() {
        assertNull(user.getProfile());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse response = userService.updateProfile(1L, profileRequest, user);

        assertNotNull(user.getProfile());
        assertEquals("Full Stack Developer", response.bio());
        assertEquals(user, user.getProfile().getUser());
    }
}