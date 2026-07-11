package com.leaguemate.api.dto;

public record UserResponse(
        Long id,
        String email,
        String username,
        String firstName,
        String lastName,
        String role
) {}