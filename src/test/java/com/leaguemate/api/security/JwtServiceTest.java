package com.leaguemate.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    @Mock
    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        // Iniettiamo i valori @Value tramite ReflectionTestUtils senza bisogno del contesto Spring
        ReflectionTestUtils.setField(jwtService, "secretKey", "bXlTdXBlclNlY3JldEtleUZvckxlYWd1ZU1hdGVBUElTZWN1cml0eTIwMjY=");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L); // 24 ore in millisecondi

        Mockito.when(mockUserDetails.getUsername()).thenReturn("manuel22");
    }

    @Test
    void generateToken_Success() {
        String token = jwtService.generateToken(mockUserDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_Success() {
        String token = jwtService.generateToken(mockUserDetails);
        String extractedUsername = jwtService.extractUsername(token);
        assertEquals("manuel22", extractedUsername);
    }

    @Test
    void isTokenValid_Success() {
        String token = jwtService.generateToken(mockUserDetails);
        boolean isValid = jwtService.isTokenValid(token, mockUserDetails);
        assertTrue(isValid);
    }

    @Test
    void isTokenValid_Failure_WrongUser() {
        String token = jwtService.generateToken(mockUserDetails);

        UserDetails wrongUser = Mockito.mock(UserDetails.class);
        Mockito.when(wrongUser.getUsername()).thenReturn("altroUtente");

        boolean isValid = jwtService.isTokenValid(token, wrongUser);
        assertFalse(isValid);
    }
}