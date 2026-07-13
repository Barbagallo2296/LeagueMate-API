package com.leaguemate.api.service;

import com.leaguemate.api.entity.User;
import com.leaguemate.api.security.JwtService;
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
    private JwtService jwtService;

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
        String expectedToken = "mocked-jwt-token";

        Mockito.when(userService.findByUsername(username)).thenReturn(sampleUser);
        Mockito.when(jwtService.generateToken(sampleUser)).thenReturn(expectedToken);

        String token = authService.login(username, password);

        assertNotNull(token);
        assertEquals(expectedToken, token);
        Mockito.verify(authenticationManager, Mockito.times(1)).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );
        Mockito.verify(userService, Mockito.times(1)).findByUsername(username);
        Mockito.verify(jwtService, Mockito.times(1)).generateToken(sampleUser);
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
        Mockito.verify(jwtService, Mockito.never()).generateToken(any(User.class));
    }
}