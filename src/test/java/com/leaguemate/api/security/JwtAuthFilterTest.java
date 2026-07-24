package com.leaguemate.api.security;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_ContinuesChain_WithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedToken_Returns401_AndStopsChain() throws Exception {
        StringWriter responseBody = new StringWriter();

        when(request.getHeader("Authorization")).thenReturn("Bearer token-non-valido");
        when(jwtService.extractUsername("token-non-valido"))
                .thenThrow(new MalformedJwtException("JWT strings must contain exactly 2 period characters"));
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response, times(1)).setStatus(401);
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(responseBody.toString().contains("Invalid or expired authentication token"));
    }

    @Test
    void malformedToken_DoesNotLeakInternalMessage() throws Exception {
        StringWriter responseBody = new StringWriter();

        when(request.getHeader("Authorization")).thenReturn("Bearer token-non-valido");
        when(jwtService.extractUsername("token-non-valido"))
                .thenThrow(new MalformedJwtException("JWT strings must contain exactly 2 period characters"));
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertFalse(responseBody.toString().contains("period characters"));
    }

    @Test
    void validToken_SetsAuthentication_AndContinuesChain() throws Exception {
        UserDetails userDetails = User.withUsername("manuel22")
                .password("irrilevante")
                .roles("ADMIN")
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.extractUsername("token-valido")).thenReturn("manuel22");
        when(userDetailsService.loadUserByUsername("manuel22")).thenReturn(userDetails);
        when(jwtService.isTokenValid("token-valido", userDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("manuel22", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }
}