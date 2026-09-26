package com.leaguemate.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET = "bXlTdXBlclNlY3JldEtleUZvckxlYWd1ZU1hdGVBUElTZWN1cml0eTIwMjY=";

    private JwtService jwtService;

    @Mock
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        // Costruttore diretto: nessun contesto Spring, scadenza 24 ore
        jwtService = new JwtService(SECRET, 86400000L);
    }

    @Test
    void generateToken_Success() {
        Mockito.when(mockUserDetails.getUsername()).thenReturn("manuel22");

        String token = jwtService.generateToken(mockUserDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_Success() {
        Mockito.when(mockUserDetails.getUsername()).thenReturn("manuel22");

        String token = jwtService.generateToken(mockUserDetails);
        String extractedUsername = jwtService.extractUsername(token);
        assertEquals("manuel22", extractedUsername);
    }

    @Test
    void isTokenValid_Success() {
        Mockito.when(mockUserDetails.getUsername()).thenReturn("manuel22");

        String token = jwtService.generateToken(mockUserDetails);
        boolean isValid = jwtService.isTokenValid(token, mockUserDetails);
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_Failure_WrongUser() {
        Mockito.when(mockUserDetails.getUsername()).thenReturn("manuel22");

        String token = jwtService.generateToken(mockUserDetails);

        UserDetails wrongUser = Mockito.mock(UserDetails.class);
        Mockito.when(wrongUser.getUsername()).thenReturn("altroUtente");

        boolean isValid = jwtService.isTokenValid(token, wrongUser);
        assertFalse(isValid);
    }

    @Test
    void constructor_Fails_WhenSecretIsMissing() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 1000L));
    }

    @Test
    void constructor_Fails_WhenSecretIsTooShort() {
        // "c2hvcnQ=" = "short": 5 byte, sotto i 32 richiesti da HS256
        assertThrows(IllegalStateException.class, () -> new JwtService("c2hvcnQ=", 1000L));
    }
}
