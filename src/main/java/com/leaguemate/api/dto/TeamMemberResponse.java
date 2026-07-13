package com.leaguemate.api.dto;

import java.time.LocalDateTime;

public record TeamMemberResponse(
        Long id,
        Long userId,
        String username,
        String firstName,
        String lastName,
        Long teamId,
        String teamName,
        String teamRole,
        LocalDateTime joinedAt
) {}