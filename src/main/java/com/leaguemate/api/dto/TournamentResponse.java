package com.leaguemate.api.dto;

import java.time.LocalDateTime;

public record TournamentResponse(
        Long id,
        String name,
        String season,
        String status,
        int pointsForWin,
        int pointsForDraw,
        LocalDateTime createdAt
) {}