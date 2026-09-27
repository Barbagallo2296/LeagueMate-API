package com.leaguemate.api.service;

import com.leaguemate.api.entity.User;
import com.leaguemate.api.dto.TokenResponse;
import com.leaguemate.api.security.TokenService;
import com.leaguemate.api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("testuser");
        sampleUser.setEmail("test@leaguemate.com");
        sampleUser.setPassword("password123");
        // Rimosso il setRole per evitare conflitti sul nome dell'Enum
    }

    @Test
    void register_Success() {
        Mockito.when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        Mockito.when(userService.registerUser(any(User.class))).thenReturn(sampleUser);

        User result = authService.register(sampleUser);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode("password123");
        Mockito.verify(userService, Mockito.times(1)).registerUser(any(User.class));
    }

    @Test
    void login_Success() {
        String username = "testuser";
        String password = "password123";
        TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 900);
        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(sampleUser, null, List.of());

        Mockito.when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticated);
        Mockito.when(tokenService.issue(authenticated)).thenReturn(expected);

        TokenResponse tokens = authService.login(username, password);

        assertNotNull(tokens);
        assertEquals(expected, tokens);
        Mockito.verify(authenticationManager, Mockito.times(1)).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );
        Mockito.verify(userService, Mockito.never()).findByUsername(anyString());
        Mockito.verify(tokenService, Mockito.times(1)).issue(authenticated);
    }

    @Test
    void login_Failure() {
        String username = "testuser";
        String password = "wrongPassword";

        Mockito.when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new AuthenticationException("Invalid credentials") {});

        assertThrows(AuthenticationException.class, () -> authService.login(username, password));
        Mockito.verify(authenticationManager, Mockito.times(1)).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );
        Mockito.verify(userService, Mockito.never()).findByUsername(anyString());
        Mockito.verify(tokenService, Mockito.never()).issue(any());
    }

    @Test
    void refresh_DelegatesToTokenService() {
        TokenResponse expected = new TokenResponse("new-access", "new-refresh", "Bearer", 900);
        Mockito.when(tokenService.refresh("old-refresh")).thenReturn(expected);

        assertEquals(expected, authService.refresh("old-refresh"));
    }

    @Test
    void logout_RevokesCurrentToken() {
        authService.logout("access-token");

        Mockito.verify(tokenService, Mockito.times(1)).revoke("access-token");
    }

    @Test
    void logoutAll_RevokesEveryTokenOfTheUser() {
        authService.logoutAll("testuser");

        Mockito.verify(tokenService, Mockito.times(1)).revokeAll("testuser");
    }
}
